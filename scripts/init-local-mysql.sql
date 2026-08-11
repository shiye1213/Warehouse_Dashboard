CREATE DATABASE IF NOT EXISTS warehouse_dashboard
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'warehouse'@'localhost' IDENTIFIED BY 'warehouse123';
CREATE USER IF NOT EXISTS 'warehouse'@'127.0.0.1' IDENTIFIED BY 'warehouse123';
GRANT ALL PRIVILEGES ON warehouse_dashboard.* TO 'warehouse'@'localhost';
GRANT ALL PRIVILEGES ON warehouse_dashboard.* TO 'warehouse'@'127.0.0.1';
FLUSH PRIVILEGES;
