package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("bom_relation")
public class BomRelationEntity {
    @TableId(value = "project_no", type = IdType.INPUT)
    private String projectNo;
    private String projectName;
    private String finishedMaterialCode;
    private String finishedMaterialName;
    private String finishedColor;
    private String finishedModel;
    private String finishedUom;
    private String componentCategory;
    private String componentMaterialCode;
    private String componentMaterialName;
    private String componentColor;
    private String componentModel;
    private String componentUom;
    private BigDecimal componentQtyPerFinishedCarton;
    private String componentQtyUom;
    private String bomRelationship;
}
