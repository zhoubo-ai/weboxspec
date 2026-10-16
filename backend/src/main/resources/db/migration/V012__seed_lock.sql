-- Serializes explicit seed runs, including runs configured with different admins.
-- Ordinary web requests never lock this row or trigger initialization.
CREATE TABLE seed_locks (
    name VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO seed_locks (name) VALUES ('menu-v1');
