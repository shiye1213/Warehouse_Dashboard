package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("inventory_age_batch_fact")
public class InventoryAgeBatchEntity {
    @TableId(value = "snapshot_date", type = IdType.INPUT)
    private LocalDate snapshotDate;
    private String ageBatchId;
    private String warehouseSkuKey;
    private String batchNo;
    private LocalDate receiptDate;
    private Integer ageDays;
    private String ageBucket;
    private BigDecimal batchOnHandQty;
    private BigDecimal batchReservedQty;
    private BigDecimal batchFrozenQty;
    private BigDecimal availableQty;
    private BigDecimal unitCost;
    private BigDecimal inventoryAmount;
    private LocalDate lastOutboundDate;
    private Integer daysSinceLastOutbound;
    @TableField("outbound_qty_30d")
    private BigDecimal outboundQty30d;
    @TableField("outbound_rate_30d")
    private BigDecimal outboundRate30d;
    private BigDecimal coverageDays;
    private String movementStatus;
    private String stagnantLevel;
    private Boolean isStagnant;
    private BigDecimal stagnantScore;
    private String priority;
    private String recommendedAction;
    private String owner;
    private String dataSource;
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
