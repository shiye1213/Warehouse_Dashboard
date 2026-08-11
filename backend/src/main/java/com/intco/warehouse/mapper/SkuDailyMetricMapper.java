package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.SkuDailyMetricEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SkuDailyMetricMapper extends BaseMapper<SkuDailyMetricEntity> {
    List<SkuDailyMetricEntity> selectJoined(@Param("warehouseId") String warehouseId);
}
