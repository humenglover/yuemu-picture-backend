# 使用 Ubuntu 基础镜像（eclipse-temurin 基于 Ubuntu）
FROM eclipse-temurin:11-jre

# 设定时区环境变量
ENV TZ=Asia/Shanghai

# 一次性完成：设置时区 + 配置清华源 + 安装字体
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    sed -i 's|http://archive.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list && \
    sed -i 's|http://security.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        fontconfig \
        fonts-dejavu-core && \
    rm -rf /var/lib/apt/lists/*

# 拷贝jar包
COPY yuemu-picture-backend.jar /app.jar

# 入口（环境变量由 docker-compose 的 environment: 注入）
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-Djava.awt.headless=true", "-jar", "/app.jar"]
