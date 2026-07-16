-- ============================================================
-- BMI 系统建表脚本 · SQLite 方言（v1.1 含扩展字段）
-- 首选数据库（plan.md 选型）：文件型、零配置、桌面端零服务。
-- 可直接在 SQLite 客户端 / JDBC（JdbcUtil.getConnection）中执行。
-- 与 docs/mysql_schema.sql 字段、约束一一对齐；仅方言差异：
--   · 无 ENGINE / CHARSET / 行内 COMMENT（字段注释以 -- 行注体现）
--   · 无 ON UPDATE（updated_at 由应用层在资料变更时显式刷新）
--   · DATETIME 在 SQLite 无原生类型，按 TEXT/REAL 亲和存储，应用层用 Timestamp 转换
--   · 自增用 INTEGER PRIMARY KEY AUTOINCREMENT
--   · 外键默认关闭，每次新连接必须执行 PRAGMA foreign_keys = ON
-- 表名：user / body_record（用户指定最终命名，无 t_ 前缀；user 在 SQLite 非保留字，
--   仍以双引号包裹以保持与 MySQL 反引号的可移植一致性）。
-- ============================================================

-- 开启外键约束（SQLite 默认关闭，每次新连接必须执行；建议写入 JdbcUtil.getConnection）
PRAGMA foreign_keys = ON;

-- ---------------- 用户表 ----------------
CREATE TABLE IF NOT EXISTS "user" (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,   -- 用户唯一标识（主键自增）
    username      TEXT    NOT NULL UNIQUE,             -- 用户名（登录账号，3-20位字母/数字/下划线，唯一）
    password_hash TEXT    NOT NULL,                    -- SHA-256 密码哈希（随机盐拼接，绝不存明文）
    salt          TEXT    NOT NULL,                    -- 随机盐值（注册时生成，抵御彩虹表）
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 账号注册时间
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 资料最后更新时间（注：SQLite 不支持 ON UPDATE，由应用层在资料变更时显式刷新）
    status        INTEGER  NOT NULL DEFAULT 1,         -- 账号状态：1=正常，0=禁用
    CONSTRAINT chk_user_status CHECK (status IN (0, 1))
);

-- ---------------- 测量记录表（核心字段 + 扩展字段） ----------------
CREATE TABLE IF NOT EXISTS body_record (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,    -- 记录唯一标识（主键自增）
    user_id       INTEGER NOT NULL,                     -- 所属用户ID（外键→user.id，级联删）
    measure_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 测量时间（可录入历史时间）
    height        NUMERIC(5,2) NOT NULL,                -- 身高cm（区间 50-250）
    weight        NUMERIC(5,2) NOT NULL,                -- 体重kg（区间 10-300）
    bmi           NUMERIC(4,1) NOT NULL,                -- BMI值，由 CalcUtil 公式计算（FR-03）
    body_fat      NUMERIC(4,1) NOT NULL,                -- 体脂率%，由 Deurenberg 公式估算（FR-04）
    -- 扩展字段（v1.1，选填，可空；NULL 表示未录入，绝不写 0 值）
    waist_circum  NUMERIC(5,2),                         -- 腰围cm（扩展，选填 30-200）
    hip_circum    NUMERIC(5,2),                         -- 臀围cm（扩展，选填 30-250）
    neck_circum   NUMERIC(5,2),                         -- 颈围cm（扩展，选填 20-80）
    wrist_circum  NUMERIC(5,2),                         -- 腕围cm（扩展，选填 10-40）
    systolic_bp   INTEGER,                              -- 收缩压·高压 mmHg（扩展，选填 50-300）
    diastolic_bp  INTEGER,                              -- 舒张压·低压 mmHg（扩展，选填 30-200）
    heart_rate    INTEGER,                              -- 静息心率 bpm（扩展，选填 30-250）
    visceral_fat  INTEGER,                              -- 内脏脂肪等级（扩展，选填 1-59）
    diseases      TEXT,                                 -- 既往疾病（逗号分隔，如 高血压,糖尿病；扩展，选填）
    photo_path    TEXT,                                 -- 体型照片本地路径（仅存路径，不存二进制；扩展，选填）
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 入库时间
    -- 外键：body_record.user_id → user.id（ON DELETE CASCADE）
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    -- 核心字段约束
    CONSTRAINT chk_record_height  CHECK (height  BETWEEN 50  AND 250),
    CONSTRAINT chk_record_weight  CHECK (weight  BETWEEN 10  AND 300),
    CONSTRAINT chk_record_bmi     CHECK (bmi > 0),
    CONSTRAINT chk_record_bodyfat CHECK (body_fat BETWEEN 0 AND 100),
    -- 扩展字段约束（NULL 豁免，与 MySQL 版 chk_record_* 一一对应）
    CONSTRAINT chk_record_waist   CHECK (waist_circum  IS NULL OR waist_circum  BETWEEN 30 AND 200),
    CONSTRAINT chk_record_hip     CHECK (hip_circum    IS NULL OR hip_circum    BETWEEN 30 AND 250),
    CONSTRAINT chk_record_neck    CHECK (neck_circum   IS NULL OR neck_circum   BETWEEN 20 AND 80),
    CONSTRAINT chk_record_wrist   CHECK (wrist_circum  IS NULL OR wrist_circum  BETWEEN 10 AND 40),
    CONSTRAINT chk_record_sysbp   CHECK (systolic_bp   IS NULL OR systolic_bp   BETWEEN 50 AND 300),
    CONSTRAINT chk_record_diabp   CHECK (diastolic_bp  IS NULL OR diastolic_bp  BETWEEN 30 AND 200),
    CONSTRAINT chk_record_hr      CHECK (heart_rate    IS NULL OR heart_rate    BETWEEN 30 AND 250),
    CONSTRAINT chk_record_visc    CHECK (visceral_fat  IS NULL OR visceral_fat  BETWEEN 1 AND 59)
);

-- ---------------- 索引 ----------------
-- 用户名唯一索引（登录/注册查重，O(1) 命中，呼应 plan.md 索引策略）
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username ON "user"(username);

-- 按用户+时间联合索引（历史查询/趋势加速，命中 RecordDao.queryByUser）
CREATE INDEX IF NOT EXISTS idx_record_user_time ON body_record(user_id, measure_time);

-- 按用户+主键倒序（分页列表加速，命中 RecordDao.queryByUserPage，v1.1 新增）
CREATE INDEX IF NOT EXISTS idx_record_user_id ON body_record(user_id, id DESC);
