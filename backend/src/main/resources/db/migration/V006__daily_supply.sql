CREATE TABLE daily_menus (
 delivery_date DATE NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0,
 updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(updated_by) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK(revision BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB;
CREATE TABLE daily_supply (
 delivery_date DATE NOT NULL,
 dish_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scheduled BOOLEAN NOT NULL DEFAULT TRUE,
 total_quantity INT NOT NULL, allocated_quantity INT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(delivery_date,dish_id),
 FOREIGN KEY(delivery_date) REFERENCES daily_menus(delivery_date) ON DELETE RESTRICT,
 FOREIGN KEY(dish_id) REFERENCES dishes(id) ON DELETE RESTRICT,
 CHECK(scheduled IN (0,1)), CHECK(total_quantity>=0 AND allocated_quantity>=0 AND allocated_quantity<=total_quantity),
 CHECK(version BETWEEN 0 AND 9007199254740991),
 INDEX ix_supply_dish_date(dish_id,delivery_date)
) ENGINE=InnoDB;
