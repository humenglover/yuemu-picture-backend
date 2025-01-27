package com.lumenglover.yuemupicturebackend.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.mapper.PictureMapper;
import com.lumenglover.yuemupicturebackend.mapper.UserMapper;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ElasticSearchSyncJob implements CommandLineRunner {

    private static final String PICTURE_INDEX = "picture";
    private static final String USER_INDEX = "user";

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private UserMapper userMapper;

    // 记录上次同步时间
    private Date lastSyncTime = new Date();

    /**
     * 应用启动时执行全量同步
     */
    @Override
    public void run(String... args) {
        fullSync();
    }

    /**
     * 全量同步
     */
    public void fullSync() {
        try {
            log.info("开始全量同步数据到ES");
            // 同步图片数据
            fullSyncPictures();
            // 同步用户数据
            fullSyncUsers();
            log.info("全量同步数据到ES完成");
        } catch (Exception e) {
            log.error("全量同步数据到ES失败", e);
        }
    }

    /**
     * 每10分钟执行一次增量同步
     */
    @Scheduled(fixedRate = 600000)
    public void incrementalSync() {
        try {
            log.info("开始增量同步数据到ES");
            Date currentTime = new Date();
            // 增量同步图片
            incrementalSyncPictures(lastSyncTime, currentTime);
            // 增量同步用户
            incrementalSyncUsers(lastSyncTime, currentTime);
            // 更新上次同步时间
            lastSyncTime = currentTime;
            log.info("增量同步数据到ES完成");
        } catch (Exception e) {
            log.error("增量同步数据到ES失败", e);
        }
    }

    /**
     * 全量同步图片数据
     */
    private void fullSyncPictures() {
        // 分批查询所有未删除的图片数据
        int batchSize = 1000;
        QueryWrapper<Picture> wrapper = new QueryWrapper<Picture>()
                .eq("isDelete", 0);  // 修改为驼峰命名
        long total = pictureMapper.selectCount(wrapper);
        long pages = (total + batchSize - 1) / batchSize;

        for (int i = 0; i < pages; i++) {
            List<Picture> pictures = pictureMapper.selectList(
                    wrapper.last("limit " + i * batchSize + "," + batchSize)
            );

            if (pictures.isEmpty()) {
                continue;
            }

            // 构建索引请求
            List<IndexQuery> queries = pictures.stream()
                    .map(picture -> new IndexQueryBuilder()
                            .withId(picture.getId().toString())
                            .withObject(picture)
                            .build())
                    .collect(Collectors.toList());

            // 批量保存到ES
            elasticsearchRestTemplate.bulkIndex(queries, IndexCoordinates.of(PICTURE_INDEX));
            log.info("同步图片数据到ES: 第{}批, 数量{}", i + 1, pictures.size());
        }

        // 清理已删除的数据
        cleanDeletedPictures();
    }

    /**
     * 全量同步用户数据
     */
    private void fullSyncUsers() {
        // 分批查询所有未删除的用户数据
        int batchSize = 1000;
        QueryWrapper<User> wrapper = new QueryWrapper<User>()
                .eq("isDelete", 0);  // 修改为驼峰命名
        long total = userMapper.selectCount(wrapper);
        long pages = (total + batchSize - 1) / batchSize;

        for (int i = 0; i < pages; i++) {
            List<User> users = userMapper.selectList(
                    wrapper.last("limit " + i * batchSize + "," + batchSize)
            );

            if (users.isEmpty()) {
                continue;
            }

            // 构建索引请求
            List<IndexQuery> queries = users.stream()
                    .map(user -> new IndexQueryBuilder()
                            .withId(user.getId().toString())
                            .withObject(user)
                            .build())
                    .collect(Collectors.toList());

            // 批量保存到ES
            elasticsearchRestTemplate.bulkIndex(queries, IndexCoordinates.of(USER_INDEX));
            log.info("同步用户数据到ES: 第{}批, 数量{}", i + 1, users.size());
        }

        // 清理已删除的数据
        cleanDeletedUsers();
    }

    /**
     * 增量同步图片数据
     */
    private void incrementalSyncPictures(Date startTime, Date endTime) {
        // 查询这段时间内更新的未删除数据
        List<Picture> pictures = pictureMapper.selectList(new QueryWrapper<Picture>()
                .ge("updateTime", startTime)
                .le("updateTime", endTime)
                .eq("isDelete", 0));

        if (!pictures.isEmpty()) {
            List<IndexQuery> queries = pictures.stream()
                    .map(picture -> new IndexQueryBuilder()
                            .withId(picture.getId().toString())
                            .withObject(picture)
                            .build())
                    .collect(Collectors.toList());

            elasticsearchRestTemplate.bulkIndex(queries, IndexCoordinates.of(PICTURE_INDEX));
            log.info("增量同步图片数据到ES, 数量: {}", pictures.size());
        }

        // 处理这段时间内被删除的数据
        List<Picture> deletedPictures = pictureMapper.selectList(new QueryWrapper<Picture>()
                .ge("updateTime", startTime)
                .le("updateTime", endTime)
                .eq("isDelete", 1));

        if (!deletedPictures.isEmpty()) {
            deletedPictures.forEach(picture ->
                    elasticsearchRestTemplate.delete(picture.getId().toString(), IndexCoordinates.of(PICTURE_INDEX))
            );
            log.info("从ES删除已删除的图片, 数量: {}", deletedPictures.size());
        }
    }

    /**
     * 增量同步用户数据
     */
    private void incrementalSyncUsers(Date startTime, Date endTime) {
        // 查询这段时间内更新的未删除数据
        List<User> users = userMapper.selectList(new QueryWrapper<User>()
                .ge("updateTime", startTime)
                .le("updateTime", endTime)
                .eq("isDelete", 0));

        if (!users.isEmpty()) {
            List<IndexQuery> queries = users.stream()
                    .map(user -> new IndexQueryBuilder()
                            .withId(user.getId().toString())
                            .withObject(user)
                            .build())
                    .collect(Collectors.toList());

            elasticsearchRestTemplate.bulkIndex(queries, IndexCoordinates.of(USER_INDEX));
            log.info("增量同步用户数据到ES, 数量: {}", users.size());
        }

        // 处理这段时间内被删除的数据
        List<User> deletedUsers = userMapper.selectList(new QueryWrapper<User>()
                .ge("updateTime", startTime)
                .le("updateTime", endTime)
                .eq("isDelete", 1));

        if (!deletedUsers.isEmpty()) {
            deletedUsers.forEach(user ->
                    elasticsearchRestTemplate.delete(user.getId().toString(), IndexCoordinates.of(USER_INDEX))
            );
            log.info("从ES删除已删除的用户, 数量: {}", deletedUsers.size());
        }
    }

    /**
     * 清理已删除的图片数据
     */
    private void cleanDeletedPictures() {
        List<Picture> deletedPictures = pictureMapper.selectList(
                new QueryWrapper<Picture>().eq("isDelete", 1)
        );

        if (!deletedPictures.isEmpty()) {
            deletedPictures.forEach(picture ->
                    elasticsearchRestTemplate.delete(picture.getId().toString(), IndexCoordinates.of(PICTURE_INDEX))
            );
            log.info("清理已删除的图片数据, 数量: {}", deletedPictures.size());
        }
    }

    /**
     * 清理已删除的用户数据
     */
    private void cleanDeletedUsers() {
        List<User> deletedUsers = userMapper.selectList(
                new QueryWrapper<User>().eq("isDelete", 1)
        );

        if (!deletedUsers.isEmpty()) {
            deletedUsers.forEach(user ->
                    elasticsearchRestTemplate.delete(user.getId().toString(), IndexCoordinates.of(USER_INDEX))
            );
            log.info("清理已删除的用户数据, 数量: {}", deletedUsers.size());
        }
    }
}
