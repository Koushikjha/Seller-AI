-- =====================================================================
-- V1 — Laptop marketplace schema (MySQL 8.0+).
--
-- Design rule: the backend is the source of truth for every purchasable
-- fact (price, stock, discount, specs). Nothing in here is ever supplied
-- by the LLM. See docs/AGENT_CONTRACT.md.
--
-- MySQL notes, because these bit us on the way over from Postgres:
--   * IDs are BINARY(16). MySQL has no UUID type; Hibernate maps
--     java.util.UUID to binary(16), and @UuidGenerator already mints the
--     value in Java, so there is no DB-side default to replace.
--     Read them back with SELECT BIN_TO_UUID(id) — raw, they are garbage.
--   * Foreign keys MUST be declared table-level. MySQL parses inline
--     "col UUID REFERENCES other(id)" and then SILENTLY DISCARDS it for
--     InnoDB. Every FK below is spelled out on purpose.
--   * DATETIME(6), not TIMESTAMP. MySQL's TIMESTAMP is 1970-2038 and has
--     auto-update side effects; Hibernate maps Instant to datetime(6).
--   * BOOLEAN is stored as TINYINT(1) and reported by Connector/J as
--     Types.BIT; MySQLDialect resolves BIT(1) back to BOOLEAN, so
--     ddl-auto: validate is happy with it.
--   * ENGINE=InnoDB is explicit because SELECT ... FOR UPDATE (the stock
--     lock on close) is an InnoDB feature.
-- =====================================================================

-- ---------------------------------------------------------------------
-- catalog/ — shared reference data, reusable across device types
-- ---------------------------------------------------------------------

CREATE TABLE brand (
    id                      BINARY(16)  NOT NULL,
    name                    VARCHAR(60) NOT NULL,
    country_of_origin       VARCHAR(60),
    support_tier            VARCHAR(20),      -- PREMIUM / STANDARD / BUDGET
    default_warranty_months INT,
    brand_positioning       VARCHAR(120),
    PRIMARY KEY (id),
    CONSTRAINT uq_brand_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sub_brand (
    id                 BINARY(16)  NOT NULL,
    brand_id           BINARY(16)  NOT NULL,
    name               VARCHAR(60) NOT NULL,
    segment            VARCHAR(30),           -- GAMING / ULTRABOOK / BUSINESS / CREATOR / BUDGET
    price_tier         VARCHAR(20),           -- ENTRY / MID / FLAGSHIP
    build_quality_tier VARCHAR(60),
    target_persona     VARCHAR(60),
    PRIMARY KEY (id),
    CONSTRAINT uq_sub_brand_name UNIQUE (brand_id, name),
    CONSTRAINT fk_sub_brand_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sub_brand_brand ON sub_brand(brand_id);

-- "cpu" covers any main processor: laptop CPUs today, phone chipsets later.
CREATE TABLE cpu (
    id              BINARY(16)  NOT NULL,
    name            VARCHAR(80) NOT NULL,
    manufacturer    VARCHAR(30),
    cores           INT,
    threads         INT,
    base_clock_ghz  DECIMAL(3,1),
    boost_clock_ghz DECIMAL(3,1),
    tdp_watts       INT,
    benchmark_tier  VARCHAR(20),              -- ENTRY / MID / HIGH / FLAGSHIP
    PRIMARY KEY (id),
    CONSTRAINT uq_cpu_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE gpu (
    id             BINARY(16)  NOT NULL,
    name           VARCHAR(80) NOT NULL,
    manufacturer   VARCHAR(30),
    vram_gb        INT,
    benchmark_tier VARCHAR(20),
    is_integrated  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_gpu_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- laptop/ — the catalog item itself
-- ---------------------------------------------------------------------

CREATE TABLE laptop (
    id               BINARY(16)   NOT NULL,
    sub_brand_id     BINARY(16)   NOT NULL,
    cpu_id           BINARY(16)   NOT NULL,
    gpu_id           BINARY(16),              -- null = integrated graphics only

    model_name       VARCHAR(120)  NOT NULL,
    base_price       DECIMAL(10,2) NOT NULL,
    max_discount_pct DECIMAL(4,2)  NOT NULL DEFAULT 0,
    stock_qty        INT           NOT NULL DEFAULT 0,

    ram_gb           INT NOT NULL,
    ram_type         VARCHAR(10),
    storage_gb       INT NOT NULL,
    storage_type     VARCHAR(10),

    display_inches   DECIMAL(3,1),
    display_type     VARCHAR(20),
    refresh_rate_hz  INT,
    touchscreen      BOOLEAN NOT NULL DEFAULT FALSE,

    weight_kg        DECIMAL(3,2),
    battery_hours    INT,
    os               VARCHAR(30),
    release_year     INT,

    extra_specs      JSON,

    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_laptop_sub_brand FOREIGN KEY (sub_brand_id) REFERENCES sub_brand(id),
    CONSTRAINT fk_laptop_cpu       FOREIGN KEY (cpu_id)       REFERENCES cpu(id),
    CONSTRAINT fk_laptop_gpu       FOREIGN KEY (gpu_id)       REFERENCES gpu(id),
    CONSTRAINT ck_laptop_price     CHECK (base_price >= 0),
    CONSTRAINT ck_laptop_discount  CHECK (max_discount_pct >= 0 AND max_discount_pct <= 100),
    CONSTRAINT ck_laptop_stock     CHECK (stock_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_laptop_sub_brand ON laptop(sub_brand_id);
CREATE INDEX idx_laptop_cpu       ON laptop(cpu_id);
CREATE INDEX idx_laptop_gpu       ON laptop(gpu_id);
CREATE INDEX idx_laptop_price     ON laptop(base_price);

-- ---------------------------------------------------------------------
-- smartphone/ — thin second device type; proves the extension point.
-- Shares brand / sub_brand / cpu. Owns its own spec vocabulary.
-- ---------------------------------------------------------------------

CREATE TABLE smartphone (
    id               BINARY(16)   NOT NULL,
    sub_brand_id     BINARY(16)   NOT NULL,
    cpu_id           BINARY(16)   NOT NULL,

    model_name       VARCHAR(120)  NOT NULL,
    base_price       DECIMAL(10,2) NOT NULL,
    max_discount_pct DECIMAL(4,2)  NOT NULL DEFAULT 0,
    stock_qty        INT           NOT NULL DEFAULT 0,

    ram_gb           INT NOT NULL,
    storage_gb       INT NOT NULL,
    display_inches   DECIMAL(3,1),
    refresh_rate_hz  INT,
    battery_mah      INT,
    main_camera_mp   INT,
    os               VARCHAR(30),
    release_year     INT,

    extra_specs      JSON,

    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_smartphone_sub_brand FOREIGN KEY (sub_brand_id) REFERENCES sub_brand(id),
    CONSTRAINT fk_smartphone_cpu       FOREIGN KEY (cpu_id)       REFERENCES cpu(id),
    CONSTRAINT ck_smartphone_price     CHECK (base_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_smartphone_sub_brand ON smartphone(sub_brand_id);

-- ---------------------------------------------------------------------
-- identity / discount / order
-- ---------------------------------------------------------------------

CREATE TABLE verified_identity (
    identity_key VARCHAR(120) NOT NULL,
    ip_address   VARCHAR(45),
    verified_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (identity_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_verified_identity_ip ON verified_identity(ip_address);

CREATE TABLE discount_offer (
    id                 BINARY(16)   NOT NULL,
    laptop_id          BINARY(16)   NOT NULL,
    identity_key       VARCHAR(120) NOT NULL,
    requested_pct      DECIMAL(4,2),
    approved_pct       DECIMAL(4,2),
    negotiation_rounds INT     NOT NULL DEFAULT 0,
    expires_at         DATETIME(6) NOT NULL,
    redeemed           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_offer_laptop   FOREIGN KEY (laptop_id)    REFERENCES laptop(id),
    CONSTRAINT fk_offer_identity FOREIGN KEY (identity_key) REFERENCES verified_identity(identity_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_offer_identity_laptop ON discount_offer(identity_key, laptop_id, created_at DESC);

CREATE TABLE marketplace_order (
    id                BINARY(16)   NOT NULL,
    laptop_id         BINARY(16)   NOT NULL,
    identity_key      VARCHAR(120) NOT NULL,
    list_price        DECIMAL(10,2) NOT NULL,
    discount_pct      DECIMAL(4,2)  NOT NULL DEFAULT 0,
    final_price       DECIMAL(10,2) NOT NULL,
    discount_offer_id BINARY(16),
    status            VARCHAR(20) NOT NULL,     -- CREATED / PAID / FAILED / CANCELLED
    payment_ref       VARCHAR(80),
    payment_link      VARCHAR(500),
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_laptop   FOREIGN KEY (laptop_id)         REFERENCES laptop(id),
    CONSTRAINT fk_order_identity FOREIGN KEY (identity_key)      REFERENCES verified_identity(identity_key),
    CONSTRAINT fk_order_offer    FOREIGN KEY (discount_offer_id) REFERENCES discount_offer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_order_identity ON marketplace_order(identity_key);

-- ---------------------------------------------------------------------
-- webinfo/ — soft, web-sourced info cached per sub-brand
-- ---------------------------------------------------------------------

CREATE TABLE sub_brand_web_cache (
    id           BINARY(16)  NOT NULL,
    sub_brand_id BINARY(16)  NOT NULL,
    query_hash   VARCHAR(64) NOT NULL,
    query_text   VARCHAR(300),
    -- VARCHAR(8000), not TEXT: the entity declares @Column(length = 8000),
    -- and MySQL reports TEXT as LONGVARCHAR, which fails ddl-auto: validate.
    summary      VARCHAR(8000),
    source_count INT NOT NULL DEFAULT 0,
    retrieved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_web_cache UNIQUE (sub_brand_id, query_hash),
    CONSTRAINT fk_web_cache_sub_brand FOREIGN KEY (sub_brand_id) REFERENCES sub_brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
