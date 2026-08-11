package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("inventory_snapshot_fact")
public class InventorySnapshotEntity {
    @TableId(value = "warehouse_sku_key", type = IdType.INPUT)
    private String warehouseSkuKey;
    private LocalDate stockDate;
    private BigDecimal onHandMainQty;
    private BigDecimal reservedMainQty;
    private BigDecimal frozenMainQty;
    private BigDecimal vendorOwnedOnHandMainQty;
    @TableField(exist = false)
    private String warehouseId;
    @TableField(exist = false)
    private String warehouseName;
    @TableField(exist = false)
    private String materialCode;
    @TableField(exist = false)
    private String projectNo;
    @TableField(exist = false)
    private String materialName;
    @TableField(exist = false)
    private String customerItem;
    @TableField(exist = false)
    private String projectMaterialSku;
    @TableField(exist = false)
    private String productIndexNo;
    @TableField(exist = false)
    private String gloveSize;
    @TableField(exist = false)
    private String colorCode;
    @TableField(exist = false)
    private String mainUom;
    @TableField(exist = false)
    private String specification;
    @TableField(exist = false)
    private String model;
}
