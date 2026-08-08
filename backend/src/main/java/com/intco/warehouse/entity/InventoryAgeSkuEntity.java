package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("inventory_age_sku")
public class InventoryAgeSkuEntity {
    @TableId(value = "snapshot_date", type = IdType.INPUT)
    private LocalDate snapshotDate;
    private String warehouseId;
    private String warehouseName;
    private String warehouseType;
    private String projectNo;
    private String projectName;
    private String materialCode;
    private String materialName;
    private String projectMaterialSku;
    private String materialCategory;
    private String color;
    private String model;
    private String uom;
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
}
