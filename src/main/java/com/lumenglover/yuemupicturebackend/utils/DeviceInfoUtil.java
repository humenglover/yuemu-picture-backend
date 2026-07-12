package com.lumenglover.yuemupicturebackend.utils;

import cn.hutool.core.util.StrUtil;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.Version;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;

/**
 * 设备信息工具类
 */
@Slf4j
public class DeviceInfoUtil {

    /**
     * 设备信息
     */
    @Data
    public static class DeviceInfo {
        private String deviceType;
        private String deviceName;
        private String osType;
        private String osVersion;
        private String browserType;
        private String browserVersion;
        private String userAgent;
    }

    /**
     * 从请求中解析设备信息
     */
    public static DeviceInfo parseDeviceInfo(HttpServletRequest request) {
        DeviceInfo deviceInfo = new DeviceInfo();

        String userAgentStr = request.getHeader("User-Agent");
        deviceInfo.setUserAgent(userAgentStr);

        if (StrUtil.isBlank(userAgentStr)) {
            setDefaultDeviceInfo(deviceInfo);
            return deviceInfo;
        }

        try {
            UserAgent userAgent = UserAgent.parseUserAgentString(userAgentStr);

            // 解析操作系统
            OperatingSystem os = userAgent.getOperatingSystem();
            deviceInfo.setOsType(getOsType(os));
            deviceInfo.setOsVersion(getOsVersion(os));

            // 解析浏览器
            Browser browser = userAgent.getBrowser();
            deviceInfo.setBrowserType(getBrowserType(browser));
            Version browserVersion = userAgent.getBrowserVersion();
            deviceInfo.setBrowserVersion(browserVersion != null ? browserVersion.getVersion() : "Unknown");

            // 解析设备类型
            deviceInfo.setDeviceType(getDeviceType(os, userAgentStr));
            deviceInfo.setDeviceName(getDeviceName(os, browser));

        } catch (Exception e) {
            log.error("解析设备信息失败", e);
            setDefaultDeviceInfo(deviceInfo);
        }

        return deviceInfo;
    }

    /**
     * 获取操作系统类型
     */
    private static String getOsType(OperatingSystem os) {
        if (os == null) {
            return "Unknown";
        }

        String osName = os.getName().toLowerCase();
        if (osName.contains("windows")) {
            return "Windows";
        } else if (osName.contains("mac")) {
            return "MacOS";
        } else if (osName.contains("linux")) {
            return "Linux";
        } else if (osName.contains("ios") || osName.contains("iphone") || osName.contains("ipad")) {
            return "iOS";
        } else if (osName.contains("android")) {
            return "Android";
        }
        return "Unknown";
    }

    /**
     * 获取操作系统版本
     */
    private static String getOsVersion(OperatingSystem os) {
        if (os == null) {
            return "Unknown";
        }
        return os.getName();
    }

    /**
     * 获取浏览器类型
     */
    private static String getBrowserType(Browser browser) {
        if (browser == null) {
            return "Unknown";
        }

        String browserName = browser.getName().toLowerCase();
        if (browserName.contains("chrome")) {
            return "Chrome";
        } else if (browserName.contains("firefox")) {
            return "Firefox";
        } else if (browserName.contains("safari")) {
            return "Safari";
        } else if (browserName.contains("edge")) {
            return "Edge";
        } else if (browserName.contains("ie") || browserName.contains("internet explorer")) {
            return "IE";
        }
        return "Unknown";
    }

    /**
     * 获取设备类型
     */
    private static String getDeviceType(OperatingSystem os, String userAgent) {
        if (os == null) {
            return "Unknown";
        }

        String osName = os.getName().toLowerCase();
        String ua = userAgent.toLowerCase();

        // 判断是否为移动设备
        if (osName.contains("android") || osName.contains("ios") || osName.contains("iphone")) {
            return "Mobile";
        }

        // 判断是否为平板
        if (osName.contains("ipad") || ua.contains("tablet")) {
            return "Tablet";
        }

        // 判断是否为PC
        if (osName.contains("windows") || osName.contains("mac") || osName.contains("linux")) {
            return "PC";
        }

        return "Unknown";
    }

    /**
     * 获取设备名称
     */
    private static String getDeviceName(OperatingSystem os, Browser browser) {
        if (os == null || browser == null) {
            return "Unknown Device";
        }
        return os.getName() + " - " + browser.getName();
    }

    /**
     * 设置默认设备信息
     */
    private static void setDefaultDeviceInfo(DeviceInfo deviceInfo) {
        deviceInfo.setDeviceType("Unknown");
        deviceInfo.setDeviceName("Unknown Device");
        deviceInfo.setOsType("Unknown");
        deviceInfo.setOsVersion("Unknown");
        deviceInfo.setBrowserType("Unknown");
        deviceInfo.setBrowserVersion("Unknown");
    }
}
