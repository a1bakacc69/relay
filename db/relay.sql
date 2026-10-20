-- ============================================================
-- Relay 主库 —— 建表脚本（可执行版本）
--
-- 怎么用：
--   1. 用 MySQL 客户端连上数据库（IDEA 的 Database 面板 / Navicat / 命令行都行）
--   2. 把整个文件执行一遍
--   3. 重复执行是安全的：所有表都写了 IF NOT EXISTS，不会覆盖已有数据
--
-- 和 docs/数据库设计.md 的关系：
--   那边是**给人读的设计文档**，讲每个字段为什么这么设计；
--   这边是**给机器跑的脚本**，只有结构。字段名和索引以两边一致为准。
--
-- 商品模块（products / product_variants）是本次新增的。
-- docs/数据库设计.md 第六章还写着「product_mappings 不做」——
-- 那是当时的决策，现在业务需要，所以做了。文档不动，实现以本文件为准。
-- ============================================================

-- ★ 显式声明本次会话按 utf8mb4 收发，必须排在所有 CREATE 前面。
--   不写这句的话，Navicat 这类客户端可能按 GBK 把文件内容发过来，
--   服务器照单全收 —— 注释就存成了「鍟嗗搧鎻忚堪」这种乱码。
--   注意这是**存进去时就坏了**，不是显示问题，事后只能重写，改不回来。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `relay`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `relay`;


-- ============================================================
-- 订单模块（已有）
-- ============================================================

CREATE TABLE IF NOT EXISTS `orders` (
  `id`                     bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `platform_order_id`      bigint        NOT NULL COMMENT '平台侧订单ID',
  `order_number`           varchar(50)   NOT NULL COMMENT '订单号',
  `name`                   varchar(50)   NOT NULL COMMENT '订单显示名',
  `email`                  varchar(100)  DEFAULT NULL COMMENT '买家邮箱',
  `total_price`            decimal(10,2) DEFAULT NULL COMMENT '订单总金额',
  `subtotal_price`         decimal(10,2) DEFAULT NULL COMMENT '商品小计',
  `total_tax`              decimal(10,2) DEFAULT NULL COMMENT '税费',
  `currency`               varchar(10)   DEFAULT 'USD' COMMENT '币种',
  `shipping_name`          varchar(100)  DEFAULT NULL COMMENT '收件人姓名',
  `shipping_phone`         varchar(50)   DEFAULT NULL COMMENT '收件人电话',
  `shipping_address1`      varchar(200)  DEFAULT NULL COMMENT '收货地址1，如街道',
  `shipping_address2`      varchar(200)  DEFAULT NULL COMMENT '收货地址2，如门牌',
  `shipping_city`          varchar(100)  DEFAULT NULL COMMENT '城市',
  `shipping_zip`           varchar(20)   DEFAULT NULL COMMENT '邮编',
  `shipping_province`      varchar(100)  DEFAULT NULL COMMENT '省/州',
  `shipping_country_code`  varchar(10)   DEFAULT NULL COMMENT '国家代码，如 CA',
  `financial_status`       varchar(20)   DEFAULT NULL COMMENT '支付状态',
  `fulfillment_status`     varchar(20)   DEFAULT NULL COMMENT '发货状态',
  `created_at`             datetime      DEFAULT NULL COMMENT '平台下单时间',
  `cancelled_at`           datetime      DEFAULT NULL COMMENT '取消时间',
  `create_time`            datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '本地创建时间',
  `update_time`            datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地更新时间',
  `platform`               varchar(20)   NOT NULL DEFAULT 'SHOPIFY' COMMENT '来源平台',
  `status`                 varchar(20)   NOT NULL DEFAULT 'PENDING' COMMENT '订单状态',
  `tracking_number`        varchar(100)  DEFAULT NULL COMMENT '运单号',
  `tracking_company`       varchar(50)   DEFAULT NULL COMMENT '承运商',
  `supplier_order_id`      varchar(100)  DEFAULT NULL COMMENT '供应商订单号',
  `sync_failed`            tinyint       DEFAULT '0' COMMENT '回写是否失败：0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_order` (`platform`,`platform_order_id`),
  KEY `idx_order_number` (`order_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表（多平台）';


CREATE TABLE IF NOT EXISTS `order_items` (
  `id`                     bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`               bigint        NOT NULL COMMENT '所属订单id，关联 orders.id',
  `title`                  varchar(200)  NOT NULL COMMENT '商品名称',
  `platform_line_item_id`  bigint        DEFAULT NULL COMMENT '平台侧明细行ID',
  `variant_id`             bigint        DEFAULT NULL COMMENT '平台侧变体ID',
  `product_id`             bigint        DEFAULT NULL COMMENT '平台侧商品ID',
  `variant_title`          varchar(200)  DEFAULT NULL COMMENT '规格名称，如150cm',
  `quantity`               int           NOT NULL COMMENT '购买数量',
  `price`                  decimal(10,2) NOT NULL COMMENT '单价',
  `sku`                    varchar(50)   DEFAULT NULL COMMENT '商品编码',
  `create_time`            datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '本地创建时间',
  `update_time`            datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细表';


-- ============================================================
-- 商品模块（新增）
--
-- 这两张表装的是「我们自己的商品」，和供应商侧的 supplier_products /
-- supplier_product_variants 是两回事：
--   供应商的货 —— 不进我们的库，用时现拉（supplier_product_id 只是引用）
--   我们的货   —— 就是我们加过价、准备拿去卖的那一份
--
-- 为什么不把供应商的商品直接当成我们的：因为要**加价、改标题**，
-- 而且供应商改价不能动我们的售价。两边必须分开存。
-- ============================================================

CREATE TABLE IF NOT EXISTS `products` (
  `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `supplier_product_id`  bigint        NOT NULL COMMENT '★ 来源：供应商侧 supplier_products.id。导入幂等靠它',
  `platform_product_id`  bigint        DEFAULT NULL COMMENT '★ 发布到 Shopify 后拿到的商品 id。NULL=没发过，非NULL=更新已有商品',
  `title`                varchar(255)  NOT NULL COMMENT '商品标题。导入时取自供应商，导入后可以改',
  `description`          text                   COMMENT '商品描述',
  `image_url`            varchar(500)  DEFAULT NULL COMMENT '主图，只存外链，不做图片上传',
  `category`             varchar(64)   DEFAULT NULL COMMENT '类目',
  `price_ratio`          decimal(5,2)  DEFAULT NULL COMMENT '★ 加价倍数：售价 = 采购价 × 本值。2.00 = 卖两倍价。NULL 表示还没定价',
  `status`               varchar(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 已导入未定价 / ON_SALE 在售 / OFF_SHELF 已下架',
  `create_time`          datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '本地创建时间',
  `update_time`          datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地更新时间',
  PRIMARY KEY (`id`),
  -- ★ 同一个供应商商品只能导入一次。重复点导入时靠这个索引挡住，
  --   而不是再建一份 —— 和 orders 的 uk_platform_order 是同一个思路
  UNIQUE KEY `uk_supplier_product` (`supplier_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='我们的商品表';


CREATE TABLE IF NOT EXISTS `product_variants` (
  `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`           bigint        NOT NULL COMMENT '所属商品，关联 products.id',
  `supplier_variant_id`  bigint        NOT NULL COMMENT '★ 来源：供应商侧 supplier_product_variants.id。下单给供应商时传的就是它',
  `variant_title`        varchar(200)  NOT NULL COMMENT '规格名，如 Black / 750ml',
  `sku`                  varchar(50)   DEFAULT NULL COMMENT '供应商的货号，原样存着备查。和我们的 SKU 不是一回事',
  `cost_price`           decimal(10,2) NOT NULL COMMENT '★ A 区：采购价，来自供应商。供应商调价时同步逻辑可以覆盖它',
  `sale_price`           decimal(10,2) DEFAULT NULL COMMENT '★ B 区：售价，我们自己定。= 采购价 × products.price_ratio。同步逻辑永远不碰',
  `image_url`            varchar(500)  DEFAULT NULL COMMENT '规格图，没有就沿用商品主图',
  `create_time`          datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '本地创建时间',
  `update_time`          datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  -- 一个供应商规格只导入一次，理由同上面的 uk_supplier_product
  UNIQUE KEY `uk_supplier_variant` (`supplier_variant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='我们的商品规格表';


-- ============================================================
-- 映射表（阶段 3 新增）
--
-- 干什么用的：订单是从平台拉来的，里面的订单明细带的是**平台侧的 variant_id**；
-- 而我们下单给供应商要的是 **supplier_variant_id**。两套 ID 毫无关系，
-- 中间必须有一张表说明「谁对应谁」——
-- 这张表就是干这个的：
--
--     平台变体  ──►  我们的规格  ──►  供应商变体
--     4166417...      product_variants.id=1     supplier_variant_id=3
--                    ↑ 映射表记的是这一段
--
-- 为什么必须精确到「变体」而不是「商品」：
--   订单里买家买的是 750ml 这个具体规格。只映射到商品层的话，
--   供应商只知道要发「保温杯」，不知道发多大 —— 会发错货。
-- ============================================================

CREATE TABLE IF NOT EXISTS `product_mappings` (
  `id`                  bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `local_variant_id`    bigint      NOT NULL COMMENT '我们的规格：product_variants.id',
  `platform`            varchar(20) NOT NULL COMMENT '平台：SHOPIFY / WOOCOMMERCE',
  `platform_variant_id` bigint      NOT NULL COMMENT '平台侧变体ID。订单里 order_items.variant_id 就是它',
  `create_time`         datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '本地创建时间',
  `update_time`         datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地更新时间',
  PRIMARY KEY (`id`),
  -- ★ 一个平台变体只能指向一个本地规格。指了两个的话，订单来了不知道该发哪个货。
  --   这条约束也是「订单侧查询键」能一次索引命中的原因。
  UNIQUE KEY `uk_platform_variant` (`platform`, `platform_variant_id`),
  -- ★ 反过来同样要唯一：同一个平台上，一个本地规格只能绑一个平台变体。
  --   变体级映射本来就是一对一；不锁死的话，重复点「发布」会插出第二条，
  --   订单进来命中哪条看运气。发布逻辑已经改成先查后改，这条是数据库兜底。
  --   跨平台不受影响：同一个货源可以同时上架 SHOPIFY 和 WOOCOMMERCE，
  --   两条记录 platform 不同，各自唯一。
  UNIQUE KEY `uk_platform_local` (`platform`, `local_variant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台变体 ↔ 本地规格 对照表';


-- ============================================================
-- users —— 后台登录用户
--
-- password_hash 存的是 BCrypt 哈希（$2a$10$... 固定 60 字符），不是明文。
-- BCrypt 每次加密都会随机加盐，同一个密码两次算出来的哈希也不同，
-- 所以校验只能靠 matches(明文, 哈希)，不能拿哈希直接比。
-- 列宽留到 100，是给将来换算法留余地。
-- ============================================================

CREATE TABLE IF NOT EXISTS `users` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`      varchar(50)  NOT NULL COMMENT '登录名',
  `password_hash` varchar(100) NOT NULL COMMENT '★ BCrypt 哈希，绝不存明文',
  `display_name`  varchar(50)  DEFAULT NULL COMMENT '显示名，可为空',
  `role`          varchar(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '本地创建时间',
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本地更新时间',
  PRIMARY KEY (`id`),
  -- 登录名不能重。注册时先查一次，这里兜底并发注册
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台登录用户';
