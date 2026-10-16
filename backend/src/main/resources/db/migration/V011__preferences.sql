CREATE TABLE dietary_preferences (
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 spice VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
 flavor VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
 budget_min_cents BIGINT NULL, budget_max_cents BIGINT NULL,
 version BIGINT NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK(spice IS NULL OR spice IN ('None','Mild','Medium','Hot')),
 CHECK(flavor IS NULL OR flavor IN ('Light','Moderate','Strong')),
 CHECK(version BETWEEN 0 AND 9007199254740991),
 CHECK((budget_min_cents IS NULL AND budget_max_cents IS NULL) OR
       (budget_min_cents IS NOT NULL AND budget_max_cents IS NOT NULL AND budget_min_cents BETWEEN 0 AND 9007199254740991 AND budget_max_cents BETWEEN budget_min_cents AND 9007199254740991))
) ENGINE=InnoDB;
CREATE TABLE preference_cuisines (
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 category VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(user_id,category), FOREIGN KEY(user_id) REFERENCES dietary_preferences(user_id) ON DELETE RESTRICT,
 CHECK(category IN ('Chinese','Western','Japanese','Light Meal','Korean','Southeast Asian'))
) ENGINE=InnoDB;
CREATE TABLE preference_allergens (
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allergen VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(user_id,allergen), FOREIGN KEY(user_id) REFERENCES dietary_preferences(user_id) ON DELETE RESTRICT,
 CHECK(allergen IN ('Peanuts','Dairy','Egg','Gluten','Soy','Fish','Shellfish'))
) ENGINE=InnoDB;
