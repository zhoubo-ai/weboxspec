CREATE TABLE dishes (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 name VARCHAR(100) NOT NULL, description VARCHAR(2000) NOT NULL,
 base_price_cents BIGINT NOT NULL,
 category VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 protein_source VARCHAR(200) NOT NULL,
 spice VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 is_listed BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_dish_media FOREIGN KEY(media_id) REFERENCES media_assets(id) ON DELETE RESTRICT,
 CONSTRAINT ck_dish_price CHECK(base_price_cents BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_dish_version CHECK(version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_dish_category CHECK(category IN ('Chinese','Western','Japanese','Light Meal','Korean','Southeast Asian')),
 CONSTRAINT ck_dish_spice CHECK(spice IN ('None','Mild','Medium','Hot')),
 CONSTRAINT ck_dish_listed CHECK(is_listed IN (0,1)),
 CONSTRAINT ck_dish_text CHECK(CHAR_LENGTH(TRIM(name))>0 AND CHAR_LENGTH(TRIM(description))>0 AND CHAR_LENGTH(TRIM(protein_source))>0),
 INDEX ix_dish_listed_category_name (is_listed,category,name,id), INDEX ix_dish_name(name,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE dish_allergens (
 dish_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allergen VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(dish_id,allergen),
 FOREIGN KEY(dish_id) REFERENCES dishes(id) ON DELETE RESTRICT,
 CHECK(allergen IN ('Peanuts','Dairy','Egg','Gluten','Soy','Fish','Shellfish'))
) ENGINE=InnoDB;
CREATE TABLE customization_groups (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 dish_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(100) NOT NULL,
 selection_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 required BOOLEAN NOT NULL, display_order INT NOT NULL,
 FOREIGN KEY(dish_id) REFERENCES dishes(id) ON DELETE RESTRICT,
 CHECK(selection_mode IN ('Single','Multiple')), CHECK(required IN (0,1)), CHECK(display_order>=0), CHECK(CHAR_LENGTH(TRIM(name))>0),
 INDEX ix_groups_dish(dish_id,display_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE customization_options (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 group_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(100) NOT NULL,
 extra_price_cents BIGINT NOT NULL, display_order INT NOT NULL,
 FOREIGN KEY(group_id) REFERENCES customization_groups(id) ON DELETE RESTRICT,
 CHECK(extra_price_cents BETWEEN 0 AND 9007199254740991), CHECK(display_order>=0), CHECK(CHAR_LENGTH(TRIM(name))>0),
 INDEX ix_options_group(group_id,display_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE option_allergens (
 option_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allergen VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(option_id,allergen), FOREIGN KEY(option_id) REFERENCES customization_options(id) ON DELETE RESTRICT,
 CHECK(allergen IN ('Peanuts','Dairy','Egg','Gluten','Soy','Fish','Shellfish'))
) ENGINE=InnoDB;
