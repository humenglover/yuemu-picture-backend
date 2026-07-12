package com.lumenglover.yuemupicturebackend.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutionException;

@Slf4j
@Configuration
public class QdrantConfig {

    @Value("${qdrant.host:127.0.0.1}")
    private String host;

    @Value("${qdrant.port:6334}")
    private int port;

    @Value("${qdrant.collection-name:yuemu_picture}")
    private String collectionName;

    @Value("${ai.dashscope.embedding.dimension:768}")
    private int vectorDimension;

    private QdrantClient qdrantClient;

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, port, false).build();
        return new QdrantClient(grpcClient);
    }

    /**
     * Spring Boot 容器启动完毕后，自动检查并初始化 Collection 和索引
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initCollection() {
        try {
            // 直接获取刚创建的单例
            QdrantClient client = qdrantClient();

            log.info("正在检查 Qdrant Collection [{}] 是否存在...", collectionName);
            boolean exists = client.collectionExistsAsync(collectionName).get();

            if (!exists) {
                log.info("Collection [{}] 不存在，正在自动创建，向量维度: {}...", collectionName, vectorDimension);

                VectorParams vectorParams = VectorParams.newBuilder()
                        .setSize(vectorDimension)
                        .setDistance(Distance.Cosine)
                        .build();

                client.createCollectionAsync(collectionName, vectorParams).get();
                log.info("Collection [{}] 创建成功！", collectionName);

                // 创建 Payload 索引加速检索
                createPayloadIndex(client, "picId", PayloadSchemaType.Integer);
                createPayloadIndex(client, "userId", PayloadSchemaType.Integer);
                createPayloadIndex(client, "spaceId", PayloadSchemaType.Integer);

            } else {
                log.info("Qdrant Collection [{}] 已存在，跳过初始化。", collectionName);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("初始化 Qdrant Collection 失败！请检查 Qdrant 服务是否正常运行 (host:{}, port:{})", host, port, e);
        }
    }

    private void createPayloadIndex(QdrantClient client, String fieldName, PayloadSchemaType type) {
        try {
            client.createPayloadIndexAsync(
                    collectionName,
                    fieldName,
                    type,
                    null,
                    null,
                    null,
                    null
            ).get();
            log.info("为字段 [{}] 创建了 Payload 索引 ({})", fieldName, type.name());
        } catch (Exception e) {
            log.warn("为字段 [{}] 创建 Payload 索引失败: {}", fieldName, e.getMessage());
        }
    }
}

