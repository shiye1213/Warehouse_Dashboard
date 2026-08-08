package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("kpi_target")
public class KpiTargetEntity {
    @TableId(value = "kpi_name", type = IdType.INPUT)
    private String kpiName;
    private BigDecimal targetValue;
    private String unit;
    private String warningRule;
    private String calculationDefinition;
    private String dataSource;
}
