package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("warehouse")
public class WarehouseEntity {
    @TableId
    private String warehouseId;
    private String warehouseName;
    private String warehouseType;
    private String warehouseRole;
    private Integer areaCount;
    private Integer capacityLocations;
    private String warehouseOwner;

    public String getWarehouseId() { return warehouseId; }
    public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getWarehouseType() { return warehouseType; }
    public void setWarehouseType(String warehouseType) { this.warehouseType = warehouseType; }
    public String getWarehouseRole() { return warehouseRole; }
    public void setWarehouseRole(String warehouseRole) { this.warehouseRole = warehouseRole; }
    public Integer getAreaCount() { return areaCount; }
    public void setAreaCount(Integer areaCount) { this.areaCount = areaCount; }
    public Integer getCapacityLocations() { return capacityLocations; }
    public void setCapacityLocations(Integer capacityLocations) { this.capacityLocations = capacityLocations; }
    public String getWarehouseOwner() { return warehouseOwner; }
    public void setWarehouseOwner(String warehouseOwner) { this.warehouseOwner = warehouseOwner; }
}
