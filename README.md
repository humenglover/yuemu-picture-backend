# 悦木图库 — 后端服务 | Yuemu Picture Gallery — Backend

> 基于 Spring Boot 的图片社区后端服务，为 [yuemutuku.com](https://www.yuemutuku.com/) 提供完整的 RESTful API 支持。
> Production-grade backend powering the Yuemu Picture Gallery — a full-featured image community platform.

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.6.13-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-11-orange)](https://adoptium.net/)
[![Docker](https://img.shields.io/badge/Docker-Supported-blue)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Private-red)](LICENSE)

---

## 📖 项目简介 | About

**悦木图库**是一个集图片上传、AI 创作、社区互动、RAG 智能问答于一体的综合性图片社交平台。本仓库为后端服务，采用 **Spring Boot 前后端分离架构**，提供 RESTful API、WebSocket 实时通信、SSE 流式输出等能力。

**Yuemu Picture Gallery** is a comprehensive image community platform featuring image sharing, AI-powered creation, social interaction, and RAG-based intelligent Q&A. This repository contains the backend service, built with a decoupled frontend/backend architecture on Spring Boot.

- 🏠 在线地址 | Website: [https://www.yuemutuku.com/](https://www.yuemutuku.com/)
- 📚 API 文档 | API Docs: `/api/doc.html`

---

## 📦 相关仓库 | Related Repositories

| 仓库 Repository | 说明 Description |
|:---|:---|
| [yuemu-picture-backend](https://github.com/humenglover/yuemu-picture-backend) | 🖥 **后端服务** — Spring Boot REST API（本仓库） |
| [yuemu-picture-frontend](https://github.com/humenglover/yuemu-picture-frontend) | 🎨 **前端页面** — Vue3 用户端 + 管理后台 |
| [yuemu-picture-official-docs](https://github.com/humenglover/yuemu-picture-official-docs) | 📚 **官方文档** — 功能指南、API 文档、开发手册 |
| [yuemu-picture-ai-service](https://github.com/humenglover/yuemu-picture-ai-service) | 🤖 **AI 服务** — Python RAG、YOLO、AI 图像处理 |

---

## 🛠 技术栈 | Tech Stack

| 分类 Category | 技术 Technology |
|:---|:---|
| 核心框架 | Spring Boot 2.6 • MyBatis Plus 3.5 |
| 数据库 | MySQL 8.0 • Redis 6.x |
| 搜索引擎 | MeiliSearch（全文检索） • Qdrant（向量检索） |
| 对象存储 | 腾讯云 COS（图片/文件存储） |
| 认证鉴权 | Sa-Token（RBAC 权限管理 + 微信扫码登录） |
| 实时通信 | WebSocket（聊天/通知） • SSE（AI 流式回答） |
| 分布式工具 | Redisson（分布式锁/限流） • Caffeine（本地缓存） |
| 数据库分片 | Apache ShardingSphere（动态分表） |
| 子服务 | Python FastAPI（RAG / YOLO / AI 图像处理） |
| 容器化 | Docker • Docker Compose |

---

## 🏗 系统架构 | Architecture

### 整体架构

<img alt="Overall Architecture" src="pictures/img_2.png"/>

### 分层架构

<img alt="Layered Architecture" src="pictures/img_1.png"/>

---

## ✨ 核心功能 | Core Features

### 🔐 用户与认证
- 账号密码注册/登录、邮箱验证码
- **微信扫码登录**（基于微信公众平台，支持自动注册）
- Sa-Token RBAC 权限管理（管理员/普通用户/VIP）
- 关注系统、粉丝列表、个人主页

### 🖼 图片管理
- 单图/批量上传，腾讯云 COS 存储，自动提取 EXIF 元信息
- **AI 图像处理**：扩图、去背景、人脸模糊、换背景
- **YOLO 目标检测**：上传图片或 URL 进行物体识别
- **自建以图搜图**：基于 Qdrant 向量检索的相似图片搜索
- 推荐系统：多维度评分 + 时间衰减 + 增量更新

### 💬 RAG 智能客服
- 知识库管理（TXT/PDF 上传，自动向量化入库）
- 检索增强生成问答（Qdrant 向量检索 + 通义千问大模型）
- **SSE 流式输出**（打字机效果实时回答）
- 多会话管理、历史记录
- **超长记忆（LTM）**：Redis 计数触发 + 多级摘要 + MeiliSearch 检索融合

### 🏘 空间管理
- 个人空间 / 团队空间
- 成员管理、角色分配、权限控制
- 容量统计、数据看板

### 🌐 社区与社交
- 帖子系统（发帖/编辑/标签/多级评论）
- 点赞、收藏、分享
- 私聊系统（WebSocket 实时通信）
- 表白墙、留言板、微语（说说）、活动系统

### 🎮 休闲娱乐
- 贪吃蛇（经典/无墙/竞速模式 + 排行榜）
- 2048 经典小游戏

### 🛡 运维与安全
- 接口限流（AOP + Redis，匿名/登录用户差异化策略）
- 敏感词过滤、内容审核
- 举报系统、Bug 反馈
- 管理员数据看板、系统通知
- 操作日志记录

---

## 🚀 快速开始 | Quick Start

### 环境要求 | Requirements

| 组件 | 版本 |
|:---|:---|
| JDK | 11+ |
| MySQL | 8.0+ |
| Redis | 6.x+ |
| MeiliSearch | 1.x+ |
| Qdrant | 1.x+ |
| Maven | 3.6+ |
| Python | 3.8+（RAG 子服务，可选） |

### 本地开发 | Local Development

```bash
# 1. 克隆仓库
git clone <your-repo-url>
cd yuemu-picture-backend

# 2. 创建本地环境变量文件
cp .env.example .env.dev
# 编辑 .env.dev 填入本地数据库密码等配置

# 3. 启动中间件（MySQL, Redis, MeiliSearch, Qdrant）
# 可使用 Docker Compose 单独启动中间件部分

# 4. 启动后端
mvn spring-boot:run
# 或直接在 IDE 中运行 YuemuPictureBackendApplication

# 5. 启动 Python RAG 子服务（可选）
cd python-rag/src
pip install -r requirements.txt
python main.py  # 默认端口 8001
```

### Docker 部署 | Docker Deployment

```bash
# 在服务器上创建 .env 文件（仅需一次）
# 编辑 .env 填入生产环境密码
chmod 600 .env

# 启动所有服务
docker compose up -d
```

### 环境变量配置 | Environment Variables

项目使用 **`.env` 文件** 管理所有密钥和密码，**不提交到 Git**。本地开发时 `DotenvEnvironmentPostProcessor` 自动加载 `.env.{profile}` 文件。

> 详见 [.env.example](.env.example) 查看完整变量列表。

---

## 📂 项目结构 | Project Structure

```
yuemu-picture-backend/
├── src/main/java/com/lumenglover/yuemupicturebackend/
│   ├── annotation/       # 自定义注解（权限校验、限流）
│   ├── aop/              # AOP 切面（限流、多设备登录控制）
│   ├── api/              # 第三方 API 封装（阿里云 AI、Pexels）
│   ├── config/           # Spring 配置类
│   ├── constant/         # 常量定义
│   ├── controller/       # REST 控制器
│   ├── exception/        # 全局异常处理
│   ├── init/             # 启动初始化（Bot 用户等）
│   ├── job/              # 定时任务
│   ├── manager/          # 业务管理器（COS、ShardingSphere、WebSocket）
│   ├── mapper/           # MyBatis Mapper
│   ├── model/            # 实体类、DTO、VO、枚举
│   └── service/          # 业务服务层
├── src/main/resources/
│   ├── application.yml           # 主配置
│   ├── application-dev.yml       # 开发环境配置
│   └── META-INF/spring.factories # SPI 注册
├── python-rag/                   # Python RAG 子服务
├── docker-compose.yml            # Docker 编排
├── Dockerfile                    # Java 服务镜像
└── .env.example                  # 环境变量模板
```

---

## 📄 更新日志 | Changelog

### v2.1.0 (2026-07)
- 🔒 **全面迁移至环境变量管理**：所有密码/密钥/邮箱/域名从 YAML 中移除，改为 `${ENV_VAR}` 引用
- 🗑 **移除百度以图搜图**：替换为自建 Qdrant 向量检索方案
- 🔍 **搜索引擎迁移**：Elasticsearch → MeiliSearch（全文检索）+ Qdrant（向量检索）
- 🐳 优化 Docker 部署流程，支持 `docker compose up -d` 一键启动
- 📝 完善文档

### v2.0.0 (2026-03)
- 🔐 Sa-Token 认证迁移，支持微信扫码登录
- 🤖 RAG 智能客服系统（知识库 + 流式问答 + 超长记忆）
- 🎯 YOLO 目标检测 & AI 图像处理
- 💬 社区论坛、私聊、活动系统
- 📊 管理员数据看板

### v1.0.0 (2025-01)
- 项目初始化：图片管理、用户系统、空间管理、COS 存储

---

## 📧 联系方式 | Contact

- 作者 | Author：鹿梦
- 网站 | Website：[yuemutuku.com](https://www.yuemutuku.com/)
- 邮箱 | Email：109484028@qq.com
