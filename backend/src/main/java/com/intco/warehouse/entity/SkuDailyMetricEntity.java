package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("sku_daily_metric_fact")
public class SkuDailyMetricEntity {
    @TableId(value = "biz_date", type = IdType.INPUT)
    private LocalDate bizDate;
    private String warehouseSkuKey;
    private String areaId;
    private String areaName;
    private Integer inboundOrderCount;
    private Integer inboundLineCount;
    private BigDecimal inboundQty;
    private Integer outboundOrderCount;
    private Integer outboundLineCount;
    private BigDecimal outboundQty;
    private Integer pickingTaskCount;
    private Integer forkliftTaskCount;
    private BigDecimal inventoryAccuracy;
    private BigDecimal receiptTimelyRate;
    private BigDecimal deliveryTimelyRate;
    private BigDecimal avgReceiptMinutes;
    private BigDecimal avgPickingMinutes;
    private Integer exceptionCount;
    private BigDecimal avgOutboundLeadDays;
    @TableField(exist = false)
    private String warehouseId;
    @TableField(exist = false)
    private String warehouseName;
    @TableField(exist = false)
    private String warehouseType;
    @TableField(exist = false)
    private String warehouseRole;
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
    @TableField(exist = false)
    private String packagingLevel;
}
