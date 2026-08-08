package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("inventory_age_rule")
public class InventoryAgeRuleEntity {
    @TableId(value = "rule_type", type = IdType.INPUT)
    private String ruleType;
    private String ruleName;
    private String ruleCondition;
    private String resultLevel;
    private String actionGuidance;
    private String applicableScope;
}
