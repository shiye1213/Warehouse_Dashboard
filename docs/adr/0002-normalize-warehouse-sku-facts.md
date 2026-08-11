# ADR 0002: Normalize SKU facts around a warehouse SKU base

## Status

Accepted

## Context

The source workbook repeats warehouse, project and material attributes in inventory, daily operation, exception and inventory-aging sheets. Persisting every copy made updates ambiguous and allowed facts for the same SKU to disagree.

## Decision

Use `warehouse_sku_base` at the grain of warehouse, project and material. SKU facts store `warehouse_sku_key` and their own measures only. Warehouse-grain facts reference `warehouse` directly. MyBatis XML joins restore the existing API and workbook export shape.

New fact tables use a `_fact` suffix so an existing local MySQL database can create the new model without destructive startup migration. Seed detection uses the base table, causing the final workbook to populate the new model on first upgraded startup.

## Consequences

- Common attributes have one authoritative row.
- Foreign keys reject facts whose SKU identity was not imported.
- Reads that need descriptive attributes use joins.
- Old tables may coexist until the explicit legacy cleanup script is run.
