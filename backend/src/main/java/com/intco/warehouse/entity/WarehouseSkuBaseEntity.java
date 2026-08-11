package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("warehouse_sku_base")
public class WarehouseSkuBaseEntity {
    @TableId(value = "warehouse_sku_key", type = IdType.INPUT)
    private String warehouseSkuKey;
    private String warehouseId;
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
    private String customerItem;
    private String productIndexNo;
    private String gloveSize;
    private String specification;
}
