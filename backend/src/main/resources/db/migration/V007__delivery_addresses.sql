CREATE TABLE delivery_addresses (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 address_text VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
 created_at DATETIME(6) NOT NULL, last_used_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
 UNIQUE KEY uq_address_text(user_id,address_text),
 INDEX ix_address_recent(user_id,last_used_at,id),
 CHECK(CHAR_LENGTH(TRIM(address_text)) BETWEEN 1 AND 200)
) ENGINE=InnoDB;
