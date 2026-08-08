package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("warehouse_daily_metric")
public class WarehouseDailyMetricEntity {
    @TableId(value = "biz_date", type = IdType.INPUT)
    private LocalDate bizDate;
    private String warehouseId;
    private String warehouseName;
    private String warehouseType;
    private Integer inboundOrderCount;
    private Integer outboundOrderCount;
    private BigDecimal rawInboundTon;
    private BigDecimal rawOutboundTon;
    private Integer finishedInboundCarton;
    private Integer finishedOutboundCarton;
    private Integer packagingInboundPiece;
    private Integer packagingOutboundPiece;
    private Integer pickingTaskCount;
    private Integer forkliftTaskCount;
    private BigDecimal inventoryAccuracy;
    private BigDecimal receiptTimelyRate;
    private BigDecimal deliveryTimelyRate;
    private Integer exceptionCount;
    private BigDecimal avgReceiptMinutes;
    private BigDecimal avgPickingMinutes;
    private BigDecimal dockUtilizationRate;
    private BigDecimal overtimeHours;
}
