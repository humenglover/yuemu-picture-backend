package com.lumenglover.yuemupicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL 图片上传
 */
@Service
@Slf4j
public class UrlPictureUpload extends PictureUploadTemplate {

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 1. 校验非空
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");

        // 2. 校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 3. 校验 URL 的协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址"
        );
        // 4. 发送 HEAD 请求验证文件是否存在并获取元数据
        HttpResponse httpResponse = null;
        try {
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    .execute();
            if (httpResponse.getStatus() == HttpStatus.HTTP_OK) {
                // 5. 文件存在，文件类型校验
                String contentType = httpResponse.header("Content-Type");
                if (StrUtil.isNotBlank(contentType)) {
                    // 允许的图片类型
                    final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");
                    ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                            ErrorCode.PARAMS_ERROR, "文件类型错误");
                }
                // 6. HEAD 预检文件大小（仅作为早期拦截，最终以实际下载大小为准）
                String contentLengthStr = httpResponse.header("Content-Length");
                if (StrUtil.isNotBlank(contentLengthStr)) {
                    try {
                        long contentLength = Long.parseLong(contentLengthStr);
                        final long MAX_PREFLIGHT_SIZE = 30 * 1024 * 1024L; // 30MB 预检上限
                        if (contentLength > MAX_PREFLIGHT_SIZE) {
                            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小超过30MB限制，请压缩后上传");
                        }
                    } catch (NumberFormatException e) {
                        // Content-Length格式异常，跳过预检，后续以实际下载大小为准
                    }
                } else {
                    // Content-Length缺失时记录日志，后续以实际下载大小为准
                    log.warn("HEAD请求未返回Content-Length，文件大小将在下载后校验: {}", fileUrl);
                }
            } else {
                // HEAD请求失败（如CDN拒绝），记录日志，后续以实际下载大小为准
                log.warn("HEAD请求返回非200状态码: {}，跳过预检，文件大小将在下载后校验: {}",
                        httpResponse.getStatus(), fileUrl);
            }
        } finally {
            // 记得释放资源
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件到临时目录
        HttpUtil.downloadFile(fileUrl, file);
    }
}
