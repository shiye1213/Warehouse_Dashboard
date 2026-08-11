USE warehouse_dashboard;

-- Run only after the application has imported data into the *_fact tables.
DROP TABLE IF EXISTS inventory_snapshot;
DROP TABLE IF EXISTS sku_daily_metric;
DROP TABLE IF EXISTS warehouse_daily_metric;
DROP TABLE IF EXISTS warehouse_area_snapshot;
DROP TABLE IF EXISTS exception_event;
DROP TABLE IF EXISTS inventory_age_batch;
DROP TABLE IF EXISTS inventory_age_sku;
