package com.lumenglover.yuemupicturebackend;

import org.apache.ibatis.annotations.Mapper;
import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@MapperScan("com.lumenglover.yuemupicturebackend.mapper")
@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class})
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling
public class YuemuPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuemuPictureBackendApplication.class, args);
    }

}
