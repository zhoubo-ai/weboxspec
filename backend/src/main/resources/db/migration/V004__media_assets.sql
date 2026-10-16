CREATE TABLE media_assets (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 uploaded_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_filename VARCHAR(255) NOT NULL,
 source_mime VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_bytes BIGINT NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 width INT NOT NULL, height INT NOT NULL,
 storage_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 served_mime VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 served_bytes BIGINT NOT NULL,
 source_attribution TEXT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_media_uploader FOREIGN KEY(uploaded_by) REFERENCES users(id) ON DELETE RESTRICT,
 CONSTRAINT ck_media_source CHECK(source_mime IN ('image/jpeg','image/png','image/webp') AND source_bytes BETWEEN 1 AND 5242880),
 CONSTRAINT ck_media_served CHECK(served_mime IN ('image/jpeg','image/png') AND served_bytes BETWEEN 1 AND 5242880),
 CONSTRAINT ck_media_dimensions CHECK(width BETWEEN 1 AND 8192 AND height BETWEEN 1 AND 8192 AND CAST(width AS SIGNED)*height <= 16777216)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
