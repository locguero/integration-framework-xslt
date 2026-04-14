CREATE TABLE IF NOT EXISTS poll_watermark (
    source_id  VARCHAR(64)  NOT NULL PRIMARY KEY,
    last_value VARCHAR(255) NOT NULL,
    updated_at VARCHAR(50)  NOT NULL
);

CREATE TABLE IF NOT EXISTS cron_request_type (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    source_system VARCHAR(64)  NOT NULL,
    entity_type   VARCHAR(64)  NOT NULL,
    operation     VARCHAR(64)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    notes         VARCHAR(512),
    created_at    VARCHAR(50)  NOT NULL,
    disabled_at   VARCHAR(50),
    disabled_by   VARCHAR(128),
    CONSTRAINT uq_cron_request_type UNIQUE (source_system, entity_type, operation)
);

-- Seed example request types (only insert when the table is empty)
INSERT INTO cron_request_type (name, source_system, entity_type, operation, active, notes, created_at)
SELECT 'ERP Order Create', 'ERP', 'ORDER', 'CREATE', TRUE,
       'Poll ERP for new orders and route through the integration pipeline', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM cron_request_type WHERE source_system='ERP' AND entity_type='ORDER' AND operation='CREATE');

INSERT INTO cron_request_type (name, source_system, entity_type, operation, active, notes, created_at)
SELECT 'ERP Order Update', 'ERP', 'ORDER', 'UPDATE', FALSE,
       'Poll ERP for updated orders — disabled by default', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM cron_request_type WHERE source_system='ERP' AND entity_type='ORDER' AND operation='UPDATE');
