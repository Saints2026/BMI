-- ============================================================
-- BMI 系统建表脚本 · MySQL 方言（InnoDB / utf8mb4，v1.1 含扩展字段）
-- 可直接复制到 MySQL 客户端执行。
-- 注意：user 为 MySQL 保留字，表名与字段引用一律反引号包裹。
-- 要求：MySQL 8.0+（CHECK 约束生效）、存储引擎 InnoDB。
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(64)  NOT NULL,
    `password_hash` VARCHAR(64)  NOT NULL,
    `salt`          VARCHAR(32)  NOT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `status`        TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    CONSTRAINT `chk_user_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户身份主表（FR-01 登录注册）';

-- 测量记录表（核心字段 + 扩展字段）
CREATE TABLE IF NOT EXISTS `body_record` (
    `id`            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`       INT UNSIGNED NOT NULL,
    `measure_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `height`        DECIMAL(5,2) NOT NULL,
    `weight`        DECIMAL(5,2) NOT NULL,
    `bmi`           DECIMAL(4,1) NOT NULL,
    `body_fat`      DECIMAL(4,1) NOT NULL,
    `waist_circum`  DECIMAL(5,2) DEFAULT NULL,
    `hip_circum`    DECIMAL(5,2) DEFAULT NULL,
    `neck_circum`   DECIMAL(5,2) DEFAULT NULL,
    `wrist_circum`  DECIMAL(5,2) DEFAULT NULL,
    `systolic_bp`   SMALLINT     DEFAULT NULL,
    `diastolic_bp`  SMALLINT     DEFAULT NULL,
    `heart_rate`    SMALLINT     DEFAULT NULL,
    `visceral_fat`  TINYINT      DEFAULT NULL,
    `diseases`      VARCHAR(255) DEFAULT NULL,
    `photo_path`    VARCHAR(512) DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_record_user_time` (`user_id`, `measure_time`),
    KEY `idx_record_user_id`   (`user_id`, `id` DESC),
    CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`)
        REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `chk_record_height`  CHECK (`height`  BETWEEN 50  AND 250),
    CONSTRAINT `chk_record_weight`  CHECK (`weight`  BETWEEN 10  AND 300),
    CONSTRAINT `chk_record_bmi`     CHECK (`bmi` > 0),
    CONSTRAINT `chk_record_bodyfat` CHECK (`body_fat` BETWEEN 0 AND 100),
    CONSTRAINT `chk_record_waist`   CHECK (`waist_circum`  IS NULL OR `waist_circum`  BETWEEN 30 AND 200),
    CONSTRAINT `chk_record_hip`     CHECK (`hip_circum`    IS NULL OR `hip_circum`    BETWEEN 30 AND 250),
    CONSTRAINT `chk_record_neck`    CHECK (`neck_circum`   IS NULL OR `neck_circum`   BETWEEN 20 AND 80),
    CONSTRAINT `chk_record_wrist`   CHECK (`wrist_circum`  IS NULL OR `wrist_circum`  BETWEEN 10 AND 40),
    CONSTRAINT `chk_record_sysbp`   CHECK (`systolic_bp`   IS NULL OR `systolic_bp`   BETWEEN 50 AND 300),
    CONSTRAINT `chk_record_diabp`   CHECK (`diastolic_bp`  IS NULL OR `diastolic_bp`  BETWEEN 30 AND 200),
    CONSTRAINT `chk_record_hr`      CHECK (`heart_rate`    IS NULL OR `heart_rate`    BETWEEN 30 AND 250),
    CONSTRAINT `chk_record_visc`    CHECK (`visceral_fat`  IS NULL OR `visceral_fat`  BETWEEN 1 AND 59)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次身体测量时序表（FR-05 历史记录，含围度/体征/疾病/照片扩展字段 v1.1）';

-- 字段级 COMMENT（MySQL 支持行内 COMMENT）
ALTER TABLE `user`
    MODIFY COLUMN `username`      VARCHAR(64) NOT NULL COMMENT '用户名（登录账号，3-20位字母/数字/下划线）',
    MODIFY COLUMN `password_hash` VARCHAR(64) NOT NULL COMMENT 'SHA-256 密码哈希（随机盐拼接，绝不存明文）',
    MODIFY COLUMN `salt`          VARCHAR(32) NOT NULL COMMENT '随机盐值，注册时生成',
    MODIFY COLUMN `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号注册时间',
    MODIFY COLUMN `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '资料最后更新时间',
    MODIFY COLUMN `status`        TINYINT     NOT NULL DEFAULT 1 COMMENT '账号状态：1=正常，0=禁用';

ALTER TABLE `body_record`
    MODIFY COLUMN `user_id`       INT UNSIGNED NOT NULL COMMENT '所属用户ID（外键→user.id）',
    MODIFY COLUMN `measure_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '测量时间',
    MODIFY COLUMN `height`        DECIMAL(5,2) NOT NULL COMMENT '身高cm（区间50-250）',
    MODIFY COLUMN `weight`        DECIMAL(5,2) NOT NULL COMMENT '体重kg（区间10-300）',
    MODIFY COLUMN `bmi`           DECIMAL(4,1) NOT NULL COMMENT 'BMI值，由BmiCalculator公式计算（FR-03）',
    MODIFY COLUMN `body_fat`      DECIMAL(4,1) NOT NULL COMMENT '体脂率%，由Deurenberg公式估算（FR-04）',
    MODIFY COLUMN `waist_circum`  DECIMAL(5,2) DEFAULT NULL COMMENT '腰围cm（扩展，选填）',
    MODIFY COLUMN `hip_circum`    DECIMAL(5,2) DEFAULT NULL COMMENT '臀围cm（扩展，选填）',
    MODIFY COLUMN `neck_circum`   DECIMAL(5,2) DEFAULT NULL COMMENT '颈围cm（扩展，选填）',
    MODIFY COLUMN `wrist_circum`  DECIMAL(5,2) DEFAULT NULL COMMENT '腕围cm（扩展，选填）',
    MODIFY COLUMN `systolic_bp`   SMALLINT     DEFAULT NULL COMMENT '收缩压·高压mmHg（扩展，选填）',
    MODIFY COLUMN `diastolic_bp`  SMALLINT     DEFAULT NULL COMMENT '舒张压·低压mmHg（扩展，选填）',
    MODIFY COLUMN `heart_rate`    SMALLINT     DEFAULT NULL COMMENT '静息心率bpm（扩展，选填）',
    MODIFY COLUMN `visceral_fat`  TINYINT      DEFAULT NULL COMMENT '内脏脂肪等级1-59（扩展，选填）',
    MODIFY COLUMN `diseases`      VARCHAR(255) DEFAULT NULL COMMENT '既往疾病（逗号分隔，如高血压,糖尿病；扩展，选填）',
    MODIFY COLUMN `photo_path`    VARCHAR(512) DEFAULT NULL COMMENT '体型照片本地路径（仅存路径，不存二进制；扩展，选填）',
    MODIFY COLUMN `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间';
