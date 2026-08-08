package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("warehouse_area_snapshot")
public class WarehouseAreaSnapshotEntity {
    @TableId(value = "snapshot_date", type = IdType.INPUT)
    private LocalDate snapshotDate;
    private String warehouseId;
    private String warehouseName;
    private String warehouseType;
    private String areaId;
    private String areaName;
    private Integer capacityLocations;
    private Integer occupiedLocations;
    private Integer availableLocations;
    private BigDecimal occupancyRate;
    private Integer materialTypeCount;
    private Integer abnormalLocationCount;
    private BigDecimal frozenQty;
    private String areaOwner;
    private String status;
}
