package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.WarehouseDailyMetricEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WarehouseDailyMetricMapper extends BaseMapper<WarehouseDailyMetricEntity> {
    List<WarehouseDailyMetricEntity> selectJoined(@Param("warehouseId") String warehouseId);
}
