package com.lumenglover.yuemupicturebackend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 在 Spring Boot 启动时自动加载环境变量文件.
 *
 * 根据 spring.profiles.active 加载对应的 .env.{profile} 文件（自包含，无需 .env 基础文件）:
 *   dev  → .env.dev
 *   prod → .env.prod
 *
 * 系统环境变量优先级最高，不会被 .env 文件覆盖.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DOTENV_PROPERTY_SOURCE = "dotenvProperties";
    private static final String ENV_FILE_PREFIX = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> dotenvProps = loadDotEnv(environment.getActiveProfiles());

        if (dotenvProps.isEmpty()) {
            return;
        }

        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addAfter(
                "systemEnvironment",
                new MapPropertySource(DOTENV_PROPERTY_SOURCE, dotenvProps)
        );
    }

    /**
     * 加载 .env.{profile}，每个文件自包含所有变量.
     */
    private Map<String, Object> loadDotEnv(String[] activeProfiles) {
        Map<String, Object> props = new LinkedHashMap<>();
        Path baseDir = Paths.get(System.getProperty("user.dir"));
        boolean anyLoaded = false;

        for (String profile : activeProfiles) {
            Path profileFile = baseDir.resolve(ENV_FILE_PREFIX + "." + profile);
            if (Files.isRegularFile(profileFile)) {
                System.out.println("[dotenv] ✅ 加载: " + profileFile.getFileName());
                mergeInto(profileFile, props);
                anyLoaded = true;
            }
        }

        if (!anyLoaded) {
            System.out.println("[dotenv] ⚠️ 未找到环境变量文件！");
            if (activeProfiles.length > 0) {
                System.out.println("[dotenv]    当前 profile: " + String.join(", ", activeProfiles));
                System.out.println("[dotenv]    需要的文件: .env." + activeProfiles[0]);
            } else {
                System.out.println("[dotenv]    默认 profile 未设置，请检查 spring.profiles.active");
            }
            System.out.println("[dotenv]    请复制 .env.example 并填入真实值");
            System.out.println("[dotenv]    项目将尝试仅使用系统环境变量启动...");
        }

        return props;
    }

    /**
     * 解析一个 .env 文件并合并到 props 中（后加载的覆盖先加载的）.
     */
    private void mergeInto(Path envFile, Map<String, Object> props) {
        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eqIdx = trimmed.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eqIdx).trim();
                String value = trimmed.substring(eqIdx + 1).trim();

                // 去掉引号
                if (value.length() >= 2) {
                    char first = value.charAt(0);
                    char last = value.charAt(value.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length() - 1);
                    }
                }

                // 系统环境变量优先，不覆盖
                if (System.getenv(key) == null && StringUtils.hasText(key)) {
                    props.put(key, value);
                }
            }
        } catch (IOException e) {
            System.out.println("[dotenv] 读取 " + envFile.getFileName() + " 失败: " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
