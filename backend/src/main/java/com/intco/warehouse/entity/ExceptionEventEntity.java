package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("exception_event")
public class ExceptionEventEntity {
    @TableId(value = "event_id", type = IdType.INPUT)
    private String eventId;
    private LocalDateTime eventTime;
    private String eventType;
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
    private String packagingLevel;
    private String areaId;
    private String areaName;
    private String severity;
    private String handlingStatus;
    private String owner;
    private Integer responseMinutes;
    private BigDecimal slaHours;
    private LocalDateTime deadlineTime;
    private LocalDateTime closeTime;
    private Integer durationMinutes;
    private Boolean isSlaBreached;
    private String rootCause;
    private String actionTaken;
    private String remark;
}
