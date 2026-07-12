package com.lumenglover.yuemupicturebackend.model.dto.picture;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 批量上传进度信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadProgress implements Serializable {

    /**
     * 任务ID（用于标识本次批量上传任务）
     */
    private String taskId;

    /**
     * 当前阶段
     * - searching: 搜索图片中
     * - translating: 翻译标题和描述中
     * - uploading: 上传图片中
     * - completed: 全部完成
     * - error: 发生错误
     */
    private String stage;

    /**
     * 阶段描述
     */
    private String stageDescription;

    /**
     * 总数量
     */
    private Integer total;

    /**
     * 当前进度（已完成数量）
     */
    private Integer current;

    /**
     * 进度百分比 (0-100)
     */
    private Integer percentage;

    /**
     * 当前处理的图片标题
     */
    private String currentPictureTitle;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failCount;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 是否完成
     */
    private Boolean completed;

    private static final long serialVersionUID = 1L;

    /**
     * 创建搜索阶段进度
     */
    public static BatchUploadProgress searching(String taskId, String keyword) {
        return BatchUploadProgress.builder()
                .taskId(taskId)
                .stage("searching")
                .stageDescription("正在搜索图片: " + keyword)
                .total(0)
                .current(0)
                .percentage(5)
                .completed(false)
                .build();
    }

    /**
     * 创建翻译阶段进度
     */
    public static BatchUploadProgress translating(String taskId, Integer total) {
        return BatchUploadProgress.builder()
                .taskId(taskId)
                .stage("translating")
                .stageDescription("正在翻译 " + total + " 个标题和描述")
                .total(total)
                .current(0)
                .percentage(15)
                .completed(false)
                .build();
    }

    /**
     * 创建上传阶段进度
     */
    public static BatchUploadProgress uploading(String taskId, Integer total, Integer current,
                                                 String currentTitle, Integer successCount, Integer failCount) {
        int percentage = 20 + (int) ((current * 1.0 / total) * 75); // 20%-95%
        return BatchUploadProgress.builder()
                .taskId(taskId)
                .stage("uploading")
                .stageDescription("正在上传第 " + current + "/" + total + " 张图片")
                .total(total)
                .current(current)
                .percentage(percentage)
                .currentPictureTitle(currentTitle)
                .successCount(successCount)
                .failCount(failCount)
                .completed(false)
                .build();
    }

    /**
     * 创建完成进度
     */
    public static BatchUploadProgress completed(String taskId, Integer total, Integer successCount, Integer failCount) {
        return BatchUploadProgress.builder()
                .taskId(taskId)
                .stage("completed")
                .stageDescription("批量上传完成")
                .total(total)
                .current(total)
                .percentage(100)
                .successCount(successCount)
                .failCount(failCount)
                .completed(true)
                .build();
    }

    /**
     * 创建错误进度
     */
    public static BatchUploadProgress error(String taskId, String errorMessage) {
        return BatchUploadProgress.builder()
                .taskId(taskId)
                .stage("error")
                .stageDescription("上传失败")
                .errorMessage(errorMessage)
                .percentage(0)
                .completed(true)
                .build();
    }
}
