package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("exception_event_fact")
public class ExceptionEventEntity {
    @TableId(value = "event_id", type = IdType.INPUT)
    private String eventId;
    private LocalDateTime eventTime;
    private String eventType;
    private String warehouseId;
    private String warehouseSkuKey;
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
    @TableField(exist = false)
    private String packagingLevel;
}
