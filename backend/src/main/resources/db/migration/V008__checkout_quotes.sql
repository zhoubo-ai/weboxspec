CREATE TABLE checkout_quotes (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 delivery_date DATE NOT NULL,
 meal VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 total_quantity INT NOT NULL, total_cents BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 consumed_order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK(meal IN ('Lunch','Dinner')), CHECK(total_quantity BETWEEN 1 AND 5),
 CHECK(total_cents BETWEEN 0 AND 9007199254740991), CHECK(expires_at=TIMESTAMPADD(MINUTE,5,created_at)),
 INDEX ix_quotes_owner(user_id,id), INDEX ix_quotes_expiry(expires_at,id)
) ENGINE=InnoDB;
CREATE TABLE checkout_quote_items (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 dish_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 dish_version BIGINT NOT NULL,
 configuration_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 dish_name_snapshot VARCHAR(100) NOT NULL,
 options_snapshot JSON NOT NULL, effective_allergens_snapshot JSON NOT NULL,
 base_price_cents BIGINT NOT NULL, unit_price_cents BIGINT NOT NULL,
 quantity INT NOT NULL, subtotal_cents BIGINT NOT NULL,
 FOREIGN KEY(quote_id) REFERENCES checkout_quotes(id) ON DELETE RESTRICT,
 FOREIGN KEY(dish_id) REFERENCES dishes(id) ON DELETE RESTRICT,
 UNIQUE KEY uq_quote_configuration(quote_id,dish_id,configuration_key),
 CHECK(dish_version BETWEEN 0 AND 9007199254740991),
 CHECK(base_price_cents BETWEEN 0 AND 9007199254740991 AND unit_price_cents BETWEEN base_price_cents AND 9007199254740991),
 CHECK(quantity BETWEEN 1 AND 5 AND subtotal_cents BETWEEN 0 AND 9007199254740991),
 CHECK(CAST(unit_price_cents AS DECIMAL(20,0))*quantity=subtotal_cents),
 CHECK(JSON_TYPE(options_snapshot)='ARRAY' AND JSON_TYPE(effective_allergens_snapshot)='ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
