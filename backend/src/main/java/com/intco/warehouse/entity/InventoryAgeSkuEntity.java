package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("inventory_age_sku_fact")
public class InventoryAgeSkuEntity {
    @TableId(value = "snapshot_date", type = IdType.INPUT)
    private LocalDate snapshotDate;
    private String warehouseSkuKey;
    private Integer batchCount;
    private BigDecimal onHandQty;
    private BigDecimal availableQty;
    private BigDecimal inventoryAmount;
    private BigDecimal weightedAvgAgeDays;
    private Integer maxAgeDays;
    private String dominantAgeBucket;
    @TableField("outbound_qty_30d")
    private BigDecimal outboundQty30d;
    @TableField("outbound_rate_30d")
    private BigDecimal outboundRate30d;
    private LocalDate latestSkuOutboundDate;
    private Integer daysSinceLastSkuOutbound;
    private Integer stagnantBatchCount;
    private BigDecimal stagnantInventoryAmount;
    private BigDecimal stagnationRatio;
    private String stagnantLevel;
    private Boolean isStagnant;
    private BigDecimal stagnantScore;
    private String priority;
    private String recommendedAction;
    private String owner;
    @TableField(exist = false)
    private String warehouseId;
    @TableField(exist = false)
    private String warehouseName;
    @TableField(exist = false)
    private String warehouseType;
    @TableField(exist = false)
    private String projectNo;
    @TableField(exist = false)
    private String projectName;
    @TableField(exist = false)
    private String materialCode;
    @TableField(exist = false)
    private String materialName;
    @TableField(exist = false)
    private String projectMaterialSku;
    @TableField(exist = false)
    private String materialCategory;
    @TableField(exist = false)
    private String color;
    @TableField(exist = false)
    private String model;
    @TableField(exist = false)
    private String uom;
}
