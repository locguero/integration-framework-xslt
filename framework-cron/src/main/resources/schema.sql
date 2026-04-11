CREATE TABLE IF NOT EXISTS poll_watermark (
    source_id  VARCHAR(64)  NOT NULL PRIMARY KEY,
    last_value VARCHAR(255) NOT NULL,
    updated_at VARCHAR(50)  NOT NULL
);
