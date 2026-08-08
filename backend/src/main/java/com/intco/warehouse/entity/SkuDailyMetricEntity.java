package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("sku_daily_metric")
public class SkuDailyMetricEntity {
    @TableId(value = "biz_date", type = IdType.INPUT)
    private LocalDate bizDate;
    private String warehouseId;
    private String warehouseName;
    private String warehouseType;
    private String warehouseRole;
    private String projectNo;
    private String projectName;
    private String materialCode;
    private String materialName;
    private String projectMaterialSku;
    private String warehouseSkuKey;
    private String materialCategory;
    private String color;
    private String model;
    private String uom;
    private String packagingLevel;
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
}
