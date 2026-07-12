package com.lumenglover.yuemupicturebackend.init;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.config.PexelsConfig;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Pexels 机器人用户初始化
 */
@Component
@Slf4j
public class BotUserInitializer implements CommandLineRunner {

    @Resource
    private UserService userService;

    @Resource
    private PexelsConfig pexelsConfig;

    @Override
    public void run(String... args) {
        if (!pexelsConfig.getEnabled()) {
            log.info("Pexels 功能未启用，跳过机器人初始化");
            return;
        }

        try {
            // 1. 检查是否存在机器人用户（仅通过 userAccount 唯一标识）
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", pexelsConfig.getBot().getUserAccount());
            User botUser = userService.getOne(queryWrapper);

            if (botUser == null) {
                // 2. 创建机器人用户
                botUser = new User();
                botUser.setUserAccount(pexelsConfig.getBot().getUserAccount());
                botUser.setUserName(pexelsConfig.getBot().getUsername());
                botUser.setUserRole(pexelsConfig.getBot().getUserRole());
                botUser.setIsBot(1);

                // 3. 密码加密（使用与普通用户相同的加密方式）
                String encryptedPassword = userService.getEncryptPassword(
                        pexelsConfig.getBot().getPassword()
                );
                botUser.setUserPassword(encryptedPassword);

                // 4. 设置其他信息
                botUser.setUserProfile(pexelsConfig.getBot().getUserProfile());
                botUser.setUserAvatar(pexelsConfig.getBot().getAvatarUrl());

                // 设置邮箱（可选）
                botUser.setEmail(pexelsConfig.getBot().getUserAccount() + "@bot.local");

                // 5. 保存到数据库
                boolean saved = userService.save(botUser);
                if (saved) {
                    log.info("✅ Pexels 机器人用户创建成功: {} (ID: {})",
                            botUser.getUserName(), botUser.getId());
                } else {
                    log.error("❌ Pexels 机器人用户创建失败");
                }
            } else {
                log.info("✅ Pexels 机器人用户已存在: {} (ID: {})",
                        botUser.getUserName(), botUser.getId());
            }
        } catch (Exception e) {
            log.error("❌ Pexels 机器人用户初始化失败", e);
        }
    }
}
