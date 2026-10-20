-- ============================================================
-- 项目改名：Dsers → Relay 的数据库迁移
--
-- 干什么：
--   把旧库的两张表**连数据一起**搬到新库，一条不丢。
--   用 RENAME TABLE 而不是「导出再导入」—— 它是原子的、秒级完成，
--   而且不用管表结构和数据量。
--
-- 怎么用：
--   1. 整个文件执行一遍
--   2. 再执行 db/relay.sql（建商品模块的两张新表 products / product_variants）
--   3. 起服务确认链路还通，最后放开末尾的 DROP 删掉旧库
--
-- 注意：如果新库已经存在同名表，RENAME 会报错。跑到报错就说明
--       这一步之前做过了，跳过即可。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 建新库（字符集和旧库保持一致，别用默认的 latin1）
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `relay`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS `relay_supplier`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;


-- ------------------------------------------------------------
-- 2. 搬表 —— 表结构和数据一起走，旧库这两张表搬完就空了
-- ------------------------------------------------------------

-- 主库：订单模块（已有数据，必须搬）
RENAME TABLE `dsers`.`orders`      TO `relay`.`orders`;
RENAME TABLE `dsers`.`order_items` TO `relay`.`order_items`;

-- 供应商库：订单 + 商品目录（已有数据，必须搬）
RENAME TABLE `dsers_supplier`.`supplier_orders`           TO `relay_supplier`.`supplier_orders`;
RENAME TABLE `dsers_supplier`.`supplier_products`         TO `relay_supplier`.`supplier_products`;
RENAME TABLE `dsers_supplier`.`supplier_product_variants` TO `relay_supplier`.`supplier_product_variants`;


-- ------------------------------------------------------------
-- 3. 旧库这时候已经空了。确认服务跑通、数据都在之后，
--    再放开下面两行把旧库删掉 —— 不要提前删。
-- ------------------------------------------------------------
-- DROP DATABASE `dsers`;
-- DROP DATABASE `dsers_supplier`;
