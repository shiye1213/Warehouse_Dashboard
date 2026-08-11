CREATE TABLE IF NOT EXISTS warehouse (
  warehouse_id VARCHAR(32) PRIMARY KEY,
  warehouse_name VARCHAR(100) NOT NULL,
  warehouse_type VARCHAR(50) NOT NULL,
  warehouse_role VARCHAR(50),
  area_count INT NOT NULL DEFAULT 0,
  capacity_locations INT NOT NULL DEFAULT 0,
  warehouse_owner VARCHAR(200),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS warehouse_sku_base (
  warehouse_sku_key VARCHAR(220) PRIMARY KEY,
  warehouse_id VARCHAR(32) NOT NULL,
  project_no VARCHAR(64) NOT NULL,
  project_name VARCHAR(200),
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(200) NOT NULL,
  project_material_sku VARCHAR(160) NOT NULL,
  material_category VARCHAR(50),
  color VARCHAR(100),
  model VARCHAR(255),
  uom VARCHAR(30) NOT NULL,
  packaging_level VARCHAR(50),
  customer_item VARCHAR(100),
  product_index_no VARCHAR(100),
  glove_size VARCHAR(100),
  specification VARCHAR(255),
  CONSTRAINT fk_sku_base_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (warehouse_id),
  CONSTRAINT uk_sku_base_scope UNIQUE (warehouse_id, project_no, material_code),
  INDEX idx_sku_base_project_material (project_no, material_code)
);

CREATE TABLE IF NOT EXISTS inventory_snapshot_fact (
  warehouse_sku_key VARCHAR(220) NOT NULL,
  stock_date DATE NOT NULL,
  on_hand_main_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  reserved_main_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  frozen_main_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  vendor_owned_on_hand_main_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  PRIMARY KEY (warehouse_sku_key, stock_date),
  CONSTRAINT fk_inventory_fact_sku FOREIGN KEY (warehouse_sku_key) REFERENCES warehouse_sku_base (warehouse_sku_key),
  INDEX idx_inventory_fact_date (stock_date)
);

CREATE TABLE IF NOT EXISTS sku_daily_metric_fact (
  biz_date DATE NOT NULL,
  warehouse_sku_key VARCHAR(220) NOT NULL,
  area_id VARCHAR(64) NOT NULL,
  area_name VARCHAR(150) NOT NULL,
  inbound_order_count INT NOT NULL DEFAULT 0,
  inbound_line_count INT NOT NULL DEFAULT 0,
  inbound_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  outbound_order_count INT NOT NULL DEFAULT 0,
  outbound_line_count INT NOT NULL DEFAULT 0,
  outbound_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  picking_task_count INT NOT NULL DEFAULT 0,
  forklift_task_count INT NOT NULL DEFAULT 0,
  inventory_accuracy DECIMAL(10,6),
  receipt_timely_rate DECIMAL(10,6),
  delivery_timely_rate DECIMAL(10,6),
  avg_receipt_minutes DECIMAL(12,2),
  avg_picking_minutes DECIMAL(12,2),
  exception_count INT NOT NULL DEFAULT 0,
  avg_outbound_lead_days DECIMAL(12,2),
  PRIMARY KEY (biz_date, warehouse_sku_key),
  CONSTRAINT fk_sku_metric_fact_sku FOREIGN KEY (warehouse_sku_key) REFERENCES warehouse_sku_base (warehouse_sku_key),
  INDEX idx_sku_metric_fact_date (biz_date),
  INDEX idx_sku_metric_fact_area (area_id)
);

CREATE TABLE IF NOT EXISTS warehouse_daily_metric_fact (
  biz_date DATE NOT NULL,
  warehouse_id VARCHAR(32) NOT NULL,
  inbound_order_count INT NOT NULL DEFAULT 0,
  outbound_order_count INT NOT NULL DEFAULT 0,
  raw_inbound_ton DECIMAL(20,4) NOT NULL DEFAULT 0,
  raw_outbound_ton DECIMAL(20,4) NOT NULL DEFAULT 0,
  finished_inbound_carton INT NOT NULL DEFAULT 0,
  finished_outbound_carton INT NOT NULL DEFAULT 0,
  packaging_inbound_piece INT NOT NULL DEFAULT 0,
  packaging_outbound_piece INT NOT NULL DEFAULT 0,
  picking_task_count INT NOT NULL DEFAULT 0,
  forklift_task_count INT NOT NULL DEFAULT 0,
  inventory_accuracy DECIMAL(10,6) NOT NULL DEFAULT 0,
  receipt_timely_rate DECIMAL(10,6) NOT NULL DEFAULT 0,
  delivery_timely_rate DECIMAL(10,6) NOT NULL DEFAULT 0,
  exception_count INT NOT NULL DEFAULT 0,
  avg_receipt_minutes DECIMAL(12,2) NOT NULL DEFAULT 0,
  avg_picking_minutes DECIMAL(12,2) NOT NULL DEFAULT 0,
  dock_utilization_rate DECIMAL(10,6) NOT NULL DEFAULT 0,
  overtime_hours DECIMAL(12,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (biz_date, warehouse_id),
  CONSTRAINT fk_daily_metric_fact_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (warehouse_id),
  INDEX idx_daily_metric_fact_warehouse_date (warehouse_id, biz_date)
);

CREATE TABLE IF NOT EXISTS warehouse_area_snapshot_fact (
  snapshot_date DATE NOT NULL,
  warehouse_id VARCHAR(32) NOT NULL,
  area_id VARCHAR(64) NOT NULL,
  area_name VARCHAR(150) NOT NULL,
  capacity_locations INT NOT NULL DEFAULT 0,
  occupied_locations INT NOT NULL DEFAULT 0,
  available_locations INT NOT NULL DEFAULT 0,
  occupancy_rate DECIMAL(10,6) NOT NULL DEFAULT 0,
  material_type_count INT NOT NULL DEFAULT 0,
  abnormal_location_count INT NOT NULL DEFAULT 0,
  frozen_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  area_owner VARCHAR(100),
  status VARCHAR(30) NOT NULL,
  PRIMARY KEY (snapshot_date, warehouse_id, area_id),
  CONSTRAINT fk_area_fact_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (warehouse_id),
  INDEX idx_area_fact_warehouse_date (warehouse_id, snapshot_date)
);

CREATE TABLE IF NOT EXISTS exception_event_fact (
  event_id VARCHAR(40) PRIMARY KEY,
  event_time TIMESTAMP NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  warehouse_id VARCHAR(32) NOT NULL,
  warehouse_sku_key VARCHAR(220),
  area_id VARCHAR(64),
  area_name VARCHAR(150),
  severity VARCHAR(30) NOT NULL,
  handling_status VARCHAR(30) NOT NULL,
  owner VARCHAR(100),
  response_minutes INT,
  sla_hours DECIMAL(12,2),
  deadline_time TIMESTAMP,
  close_time TIMESTAMP,
  duration_minutes INT,
  is_sla_breached BOOLEAN NOT NULL DEFAULT FALSE,
  root_cause VARCHAR(500),
  action_taken VARCHAR(500),
  remark VARCHAR(500),
  CONSTRAINT fk_exception_fact_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (warehouse_id),
  CONSTRAINT fk_exception_fact_sku FOREIGN KEY (warehouse_sku_key) REFERENCES warehouse_sku_base (warehouse_sku_key),
  INDEX idx_exception_fact_warehouse_time (warehouse_id, event_time),
  INDEX idx_exception_fact_status (handling_status, severity)
);

CREATE TABLE IF NOT EXISTS bom_relation (
  project_no VARCHAR(64) NOT NULL, project_name VARCHAR(200) NOT NULL,
  finished_material_code VARCHAR(64) NOT NULL, finished_material_name VARCHAR(200) NOT NULL,
  finished_color VARCHAR(100), finished_model VARCHAR(255), finished_uom VARCHAR(30) NOT NULL,
  component_category VARCHAR(50) NOT NULL, component_material_code VARCHAR(64) NOT NULL,
  component_material_name VARCHAR(200) NOT NULL, component_color VARCHAR(100), component_model VARCHAR(255),
  component_uom VARCHAR(30) NOT NULL, component_qty_per_finished_carton DECIMAL(20,6) NOT NULL,
  component_qty_uom VARCHAR(30) NOT NULL, bom_relationship VARCHAR(500),
  PRIMARY KEY (project_no, finished_material_code, component_material_code)
);

CREATE TABLE IF NOT EXISTS kpi_target (
  kpi_name VARCHAR(100) PRIMARY KEY, target_value DECIMAL(20,6) NOT NULL, unit VARCHAR(30) NOT NULL,
  warning_rule VARCHAR(200) NOT NULL, calculation_definition VARCHAR(500), data_source VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS inventory_age_rule (
  rule_type VARCHAR(50) NOT NULL, rule_name VARCHAR(100) NOT NULL, rule_condition VARCHAR(500) NOT NULL,
  result_level VARCHAR(50) NOT NULL, action_guidance VARCHAR(500), applicable_scope VARCHAR(100),
  PRIMARY KEY (rule_type, rule_name)
);

CREATE TABLE IF NOT EXISTS inventory_age_batch_fact (
  snapshot_date DATE NOT NULL,
  age_batch_id VARCHAR(100) NOT NULL,
  warehouse_sku_key VARCHAR(220) NOT NULL,
  batch_no VARCHAR(100) NOT NULL,
  receipt_date DATE NOT NULL,
  age_days INT NOT NULL,
  age_bucket VARCHAR(30) NOT NULL,
  batch_on_hand_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  batch_reserved_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  batch_frozen_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  available_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  unit_cost DECIMAL(20,6), inventory_amount DECIMAL(20,2), last_outbound_date DATE,
  days_since_last_outbound INT, outbound_qty_30d DECIMAL(20,4) NOT NULL DEFAULT 0,
  outbound_rate_30d DECIMAL(12,6), coverage_days DECIMAL(20,2), movement_status VARCHAR(50),
  stagnant_level VARCHAR(50) NOT NULL, is_stagnant BOOLEAN NOT NULL DEFAULT FALSE,
  stagnant_score DECIMAL(10,2) NOT NULL DEFAULT 0, priority VARCHAR(30),
  recommended_action VARCHAR(500), owner VARCHAR(200), data_source VARCHAR(200),
  PRIMARY KEY (snapshot_date, age_batch_id),
  CONSTRAINT fk_age_batch_fact_sku FOREIGN KEY (warehouse_sku_key) REFERENCES warehouse_sku_base (warehouse_sku_key),
  INDEX idx_age_batch_fact_date (snapshot_date),
  INDEX idx_age_batch_fact_stagnant (is_stagnant, stagnant_level, priority)
);

CREATE TABLE IF NOT EXISTS inventory_age_sku_fact (
  snapshot_date DATE NOT NULL,
  warehouse_sku_key VARCHAR(220) NOT NULL,
  batch_count INT NOT NULL DEFAULT 0, on_hand_qty DECIMAL(20,4) NOT NULL DEFAULT 0,
  available_qty DECIMAL(20,4) NOT NULL DEFAULT 0, inventory_amount DECIMAL(20,2),
  weighted_avg_age_days DECIMAL(12,2), max_age_days INT NOT NULL DEFAULT 0,
  dominant_age_bucket VARCHAR(30), outbound_qty_30d DECIMAL(20,4) NOT NULL DEFAULT 0,
  outbound_rate_30d DECIMAL(12,6), latest_sku_outbound_date DATE, days_since_last_sku_outbound INT,
  stagnant_batch_count INT NOT NULL DEFAULT 0, stagnant_inventory_amount DECIMAL(20,2),
  stagnation_ratio DECIMAL(12,6), stagnant_level VARCHAR(50) NOT NULL,
  is_stagnant BOOLEAN NOT NULL DEFAULT FALSE, stagnant_score DECIMAL(10,2) NOT NULL DEFAULT 0,
  priority VARCHAR(30), recommended_action VARCHAR(500), owner VARCHAR(200),
  PRIMARY KEY (snapshot_date, warehouse_sku_key),
  CONSTRAINT fk_age_sku_fact_sku FOREIGN KEY (warehouse_sku_key) REFERENCES warehouse_sku_base (warehouse_sku_key),
  INDEX idx_age_sku_fact_date (snapshot_date),
  INDEX idx_age_sku_fact_stagnant (is_stagnant, stagnant_level, priority)
);

CREATE TABLE IF NOT EXISTS data_import_job (
  import_id VARCHAR(36) PRIMARY KEY, file_name VARCHAR(255) NOT NULL, import_type VARCHAR(30) NOT NULL,
  imported_rows INT NOT NULL DEFAULT 0, started_at TIMESTAMP NOT NULL, finished_at TIMESTAMP,
  status VARCHAR(20) NOT NULL, message VARCHAR(500)
);
