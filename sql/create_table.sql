-- 创建库
create database if not exists yuemu_picture;

-- 切换库
use yuemu_picture;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                       null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId)              -- 提升基于用户 ID 的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Tag` (
    `id` BIGINT AUTO_INCREMENT COMMENT '标签id' PRIMARY KEY,
    `tagName` VARCHAR(256) NOT NULL COMMENT '标签名称',
    `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    `editTime` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete` TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
    UNIQUE KEY `uk_tagName` (`tagName`)
) COMMENT '标签' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Category` (
    `id` BIGINT AUTO_INCREMENT COMMENT '分类id' PRIMARY KEY,
    `categoryName` VARCHAR(256) NOT NULL COMMENT '分类名称',
    `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    `editTime` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '分类编辑时间',
    `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '分类更新时间',
    `isDelete` TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
    UNIQUE KEY `uk_categoryName` (`categoryName`)
) COMMENT '分类' COLLATE = utf8mb4_unicode_ci;

ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus INT DEFAULT 0 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    ADD COLUMN reviewMessage VARCHAR(512) NULL COMMENT '审核信息',
    ADD COLUMN reviewerId BIGINT NULL COMMENT '审核人 ID',
    ADD COLUMN reviewTime DATETIME NULL COMMENT '审核时间';

-- 创建基于 reviewStatus 列的索引
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);

ALTER TABLE picture
    -- 添加新列
    ADD COLUMN thumbnailUrl varchar(512) NULL COMMENT '缩略图 url';

-- 空间表
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 null comment '空间图片的最大数量',
    totalSize  bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    -- 索引设计
    index idx_userId (userId),        -- 提升基于用户的查询效率
    index idx_spaceName (spaceName),  -- 提升基于空间名称的查询效率
    index idx_spaceLevel (spaceLevel) -- 提升按空间级别查询的效率
) comment '空间' collate = utf8mb4_unicode_ci;

-- 添加新列
ALTER TABLE picture
    ADD COLUMN spaceId bigint  null comment '空间 id（为空表示公共空间）';

-- 创建索引
CREATE INDEX idx_spaceId ON picture (spaceId);

-- 添加新列
ALTER TABLE picture
    ADD COLUMN picColor varchar(16) null comment '图片主色调';


ALTER TABLE picture
    ADD COLUMN commentCount BIGINT NOT NULL DEFAULT 0 COMMENT '评论数',
    ADD COLUMN likeCount BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    ADD COLUMN shareCount BIGINT NOT NULL DEFAULT 0 COMMENT '分享数';

CREATE TABLE pictureLike (
                             id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
                             userId        bigint NOT NULL COMMENT '用户 ID',
                             pictureId     bigint NOT NULL COMMENT '图片 ID',
                             isLiked       BOOLEAN NOT NULL COMMENT '用户是否点赞（true 表示点赞，false 表示取消点赞）',
                             firstLikeTime datetime NOT NULL COMMENT '第一次点赞的时间',
                             lastLikeTime datetime NOT NULL COMMENT '最近一次点赞的时间'
) COMMENT '点赞表' COLLATE = utf8mb4_unicode_ci;

-- 联合索引
CREATE INDEX idx_userId_pictureId_isLiked ON pictureLike (userId, pictureId, isLiked);

ALTER TABLE pictureLike
    MODIFY COLUMN firstLikeTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE comments
(
    commentId       bigint AUTO_INCREMENT
        PRIMARY KEY,
    userId          bigint                               NOT NULL,
    pictureId       bigint                               NOT NULL,
    content         text                                 NOT NULL,
    createTime      datetime   DEFAULT CURRENT_TIMESTAMP NULL,
    parentCommentId bigint     DEFAULT 0                 NULL COMMENT '0表示顶级',
    isDelete        tinyint(1) DEFAULT 0                 NULL,
    likeCount       bigint     DEFAULT 0                 NULL,
    dislikeCount    bigint     DEFAULT 0                 NULL
);

CREATE INDEX idx_pictureId
    ON comments (pictureId);

CREATE TABLE `message` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                           `content` text NOT NULL COMMENT '留言内容',
                           `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `isDelete` tinyint(1) DEFAULT 0 COMMENT '是否删除(0-未删除 1-已删除)',
                           `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
                           PRIMARY KEY (`id`),
                           KEY `idx_createTime` (`createTime`) -- 创建时间索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='留言板表';

-- 支持空间类型，添加新列
ALTER TABLE space
    ADD COLUMN spaceType int default 0 not null comment '空间类型：0-私有 1-团队';

CREATE INDEX idx_spaceType ON space (spaceType);

-- 空间成员表
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    -- 索引设计
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 唯一索引，用户在一个空间中只能有一个角色
    INDEX idx_spaceId (spaceId),                    -- 提升按空间查询的性能
    INDEX idx_userId (userId)                       -- 提升按用户查询的性能
) comment '空间用户关联' collate = utf8mb4_unicode_ci;

CREATE TABLE userfollows
(
    followId            bigint AUTO_INCREMENT
        PRIMARY KEY,
    followerId          bigint                             NOT NULL COMMENT '关注者的用户 ID',
    followingId         bigint                             NOT NULL COMMENT '被关注者的用户 ID',
    followStatus        tinyint                            NULL COMMENT '关注状态，0 表示取消关注，1 表示关注',
    isMutual            tinyint                            NULL COMMENT '是否为双向关注，0 表示单向，1 表示双向',
    lastInteractionTime datetime                           NULL COMMENT '最后交互时间',
    createTime          datetime DEFAULT CURRENT_TIMESTAMP NULL COMMENT '关注关系创建时间，默认为当前时间',
    editTime            datetime DEFAULT CURRENT_TIMESTAMP NULL COMMENT '关注关系编辑时间，默认为当前时间',
    updateTime          datetime DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '关注关系更新时间，更新时自动更新',
    isDelete            tinyint  DEFAULT 0                 NULL COMMENT '是否删除，0 表示未删除，1 表示已删除'
);

CREATE INDEX idx_followStatus
    ON userfollows (followStatus);

CREATE INDEX idx_followerId
    ON userfollows (followerId);

CREATE INDEX idx_followingId
    ON userfollows (followingId);

CREATE INDEX idx_isDelete
    ON userfollows (isDelete);


-- 用户签到记录表（使用bitmap存储）
CREATE TABLE IF NOT EXISTS user_sign_in_record (
    id BIGINT NOT NULL COMMENT 'id' PRIMARY KEY,
    userId BIGINT NOT NULL COMMENT '用户id',
    year INT NOT NULL COMMENT '年份',
    signInData BINARY(46) NOT NULL COMMENT '签到数据位图(366天/8≈46字节)',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_userId_year (userId, year) COMMENT '用户id和年份唯一索引'
) COMMENT '用户签到记录表' COLLATE = utf8mb4_unicode_ci;


