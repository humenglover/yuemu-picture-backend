package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.PythonApiService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.http.HttpHeaders;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.http.HttpStatus;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.http.MediaType;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.http.ResponseEntity;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.PostMapping;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.RequestMapping;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.RequestPart;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.multipart.MultipartFile;

import cn.dev33.satoken.annotation.SaCheckLogin;
import javax.annotation.Resource;

/**
 * 专门用于对接隔离后端 Python 微服务生态的 Controller 接口开始
 */
@RestController
@RequestMapping("/python_api")
@Slf4j
public class PythonApiController {

    @Resource
    private PythonApiService pythonApiService;

    /**
     * 智能去除图片背景 (透传至 Python)
     * 直接返回带透明背景的 PNG 图片字节流 (不落库/不存云)
     *
     * @param file 原图片文件
     * @return 图片字节流 ResponseEntity
     */
    @PostMapping("/remove_bg")
    @SaCheckLogin // 控制基本登录可用
    @com.lumenglover.yuemupicturebackend.annotation.RateLimiter(key = "ai_remove_bg", time = 60, count = 5, message = "AI 抠图过于频繁，请稍后再试")
    public ResponseEntity<byte[]> removeBackground(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传图片");
        }

        try {
            // 调用 Service 执行并转发至 Python 端，获取扣除背景后的字节流
            byte[] resultBytes = pythonApiService.removeBackground(file);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            // 设定浏览器展示为内联，配合文件名设定
            headers.setContentDispositionFormData("inline", "no_bg_" + file.getOriginalFilename());

            return new ResponseEntity<>(resultBytes, headers, HttpStatus.OK);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python 端抠图请求处理 Controller 层异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "请求处理失败");
        }
    }

    /**
     * 智能人脸打码 (透传至 Python)
     */
    @PostMapping("/face_blur")
    @SaCheckLogin
    @com.lumenglover.yuemupicturebackend.annotation.RateLimiter(key = "ai_face_blur", time = 60, count = 5, message = "人脸打码过于频繁，请稍后再试")
    public ResponseEntity<byte[]> faceBlur(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传图片");
        }

        try {
            byte[] resultBytes = pythonApiService.faceBlur(file);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("inline", "blur_" + file.getOriginalFilename());

            return new ResponseEntity<>(resultBytes, headers, HttpStatus.OK);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python 端人脸打码请求处理 Controller 层异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "人脸打码请求处理失败");
        }
    }

    /**
     * 智能更换图片背景 (MODNet)
     */
    @PostMapping("/change_background")
    @SaCheckLogin
    @com.lumenglover.yuemupicturebackend.annotation.RateLimiter(key = "ai_change_bg", time = 60, count = 5, message = "更换背景过于频繁，请稍后再试")
    public ResponseEntity<byte[]> changeBackground(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "color", required = false) String color,
            @RequestPart(value = "background_image", required = false) MultipartFile backgroundImage) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传图片");
        }

        try {
            byte[] resultBytes = pythonApiService.changeBackground(file, color, backgroundImage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("inline", "bg_change_" + file.getOriginalFilename());

            return new ResponseEntity<>(resultBytes, headers, HttpStatus.OK);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python 端换背景请求处理 Controller 层异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "换背景请求处理失败");
        }
    }

    /**
     * 智能增强清晰度 (透传至 Python)
     */
    @PostMapping("/enhance_image")
    @SaCheckLogin
    @com.lumenglover.yuemupicturebackend.annotation.RateLimiter(key = "ai_enhance_image", time = 60, count = 5, message = "增强清晰度过于频繁，请稍后再试")
    public ResponseEntity<byte[]> enhanceImage(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传图片");
        }

        try {
            byte[] resultBytes = pythonApiService.enhanceImage(file);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setContentDispositionFormData("inline", "enhanced_" + file.getOriginalFilename());

            return new ResponseEntity<>(resultBytes, headers, HttpStatus.OK);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python 端增强清晰度请求处理 Controller 层异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "增强清晰度请求处理失败");
        }
    }
}
