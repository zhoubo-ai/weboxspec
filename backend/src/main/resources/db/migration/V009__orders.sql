CREATE TABLE orders (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 delivery_date DATE NOT NULL, meal VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 delivery_address_snapshot VARCHAR(200) NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('Pending','Confirmed') THEN 1 ELSE NULL END) STORED,
 total_quantity INT NOT NULL, total_cents BIGINT NOT NULL, version BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL, confirmed_at DATETIME(6) NULL, completed_at DATETIME(6) NULL, cancelled_at DATETIME(6) NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
 UNIQUE KEY uq_order_key(user_id,idempotency_key), UNIQUE KEY uq_order_quote(quote_id),
 UNIQUE KEY uq_order_active(user_id,delivery_date,meal,active_slot),
 INDEX ix_order_employee(user_id,created_at,id), INDEX ix_order_operations(delivery_date,status,meal,id), INDEX ix_order_recent(user_id,delivery_date,status,id),
 CHECK(meal IN ('Lunch','Dinner')), CHECK(status IN ('Pending','Confirmed','Completed','Cancelled')),
 CHECK(total_quantity BETWEEN 1 AND 5 AND total_cents BETWEEN 0 AND 9007199254740991), CHECK(version BETWEEN 0 AND 9007199254740991),
 CHECK(CHAR_LENGTH(TRIM(delivery_address_snapshot)) BETWEEN 1 AND 200),
 CHECK((status='Pending' AND confirmed_at IS NULL AND completed_at IS NULL AND cancelled_at IS NULL)
    OR (status='Confirmed' AND confirmed_at IS NOT NULL AND completed_at IS NULL AND cancelled_at IS NULL)
    OR (status='Completed' AND confirmed_at IS NOT NULL AND completed_at IS NOT NULL AND cancelled_at IS NULL)
    OR (status='Cancelled' AND confirmed_at IS NULL AND completed_at IS NULL AND cancelled_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- The quote points to its consumed order. Orders keep the original quote UUID without a FK,
-- so expired quote cleanup never makes order history or successful-key replay depend on it.
ALTER TABLE checkout_quotes ADD CONSTRAINT fk_quote_consumed_order FOREIGN KEY(consumed_order_id) REFERENCES orders(id) ON DELETE RESTRICT;
