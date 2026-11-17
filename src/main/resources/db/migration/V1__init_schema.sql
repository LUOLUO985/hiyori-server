-- Hiyori 亚超：初始表结构（MySQL 8；本地开发用 H2 的 MySQL 兼容模式跑同一份脚本）
-- 带 _en 的列是英文版文案，小程序按用户选的语言取用。

CREATE TABLE category (
    id             VARCHAR(32)  NOT NULL COMMENT '分类编码，如 sauce；deals 是「特价」虚拟分类',
    name           VARCHAR(32)  NOT NULL COMMENT '分类名',
    name_en        VARCHAR(64)  NOT NULL DEFAULT '',
    emoji          VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '没有图标时用的 emoji',
    image          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '图标 URL',
    description    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '副标题',
    description_en VARCHAR(128) NOT NULL DEFAULT '',
    sort           INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE product (
    id          VARCHAR(32)   NOT NULL COMMENT '商品编号；导入数据用 p101 / f001 这类编号，后台新建的用雪花 ID',
    category_id VARCHAR(32)   NOT NULL,
    name        VARCHAR(128)  NOT NULL COMMENT '中文名',
    name_en     VARCHAR(160)  NOT NULL DEFAULT '' COMMENT '英文名',
    spec        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '规格，如 120g',
    spec_en     VARCHAR(64)   NOT NULL DEFAULT '',
    price       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价签原价；0 表示到店询价',
    sale_price  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '特价；0 表示没有特价',
    promo       VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '促销说明，如 2件3€ / 买1送1',
    promo_en    VARCHAR(48)   NOT NULL DEFAULT '',
    remark      VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '给顾客看的补充说明，如整箱价',
    remark_en   VARCHAR(160)  NOT NULL DEFAULT '',
    barcode     VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '价签条码',
    badge       VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '手动角标：招牌 / 热卖 / 新品',
    badge_en    VARCHAR(24)   NOT NULL DEFAULT '',
    image       VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '大图（详情页）',
    thumb       VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '缩略图（列表）；空则用大图',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    sort        INT           NOT NULL DEFAULT 0 COMMENT '越小越靠前',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_product_cat_status ON product (category_id, status, deleted, sort);

CREATE TABLE banner (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(64)  NOT NULL,
    title_en         VARCHAR(96)  NOT NULL DEFAULT '',
    sub              VARCHAR(128) NOT NULL DEFAULT '',
    sub_en           VARCHAR(160) NOT NULL DEFAULT '',
    image            VARCHAR(512) NOT NULL DEFAULT '',
    link_category_id VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '点轮播图跳到哪个分类；空则不跳',
    sort             INT          NOT NULL DEFAULT 0,
    enabled          TINYINT      NOT NULL DEFAULT 1,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 首页商品区块（本周特价、火锅专区……），老板可以在后台自己配
CREATE TABLE home_section (
    id               VARCHAR(32)  NOT NULL,
    title            VARCHAR(32)  NOT NULL,
    title_en         VARCHAR(64)  NOT NULL DEFAULT '',
    sub              VARCHAR(64)  NOT NULL DEFAULT '',
    sub_en           VARCHAR(128) NOT NULL DEFAULT '',
    link_category_id VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '点「全部」跳到哪个分类',
    sort             INT          NOT NULL DEFAULT 0,
    enabled          TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE home_section_item (
    section_id VARCHAR(32) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    sort       INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (section_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE hot_keyword (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    keyword    VARCHAR(32) NOT NULL,
    keyword_en VARCHAR(48) NOT NULL DEFAULT '',
    sort       INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    openid        VARCHAR(64)  NOT NULL,
    nickname      VARCHAR(64)  NOT NULL DEFAULT '',
    avatar        VARCHAR(512) NOT NULL DEFAULT '',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
