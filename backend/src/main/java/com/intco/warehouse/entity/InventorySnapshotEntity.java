package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("inventory_snapshot")
public class InventorySnapshotEntity {
    @TableId(value = "warehouse_id", type = IdType.INPUT)
    private String warehouseId;
    private String materialCode;
    private String projectNo;
    private LocalDate stockDate;
    private String warehouseName;
    private String materialName;
    private String customerItem;
    private String projectMaterialSku;
    private String productIndexNo;
    private String gloveSize;
    private String colorCode;
    private String mainUom;
    private String specification;
    private String model;
    private BigDecimal onHandMainQty;
    private BigDecimal reservedMainQty;
    private BigDecimal frozenMainQty;
    private BigDecimal vendorOwnedOnHandMainQty;
}
