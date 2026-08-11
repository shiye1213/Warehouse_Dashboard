package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.ExceptionEventEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ExceptionEventMapper extends BaseMapper<ExceptionEventEntity> {
    List<ExceptionEventEntity> selectJoined(@Param("warehouseId") String warehouseId, @Param("areaId") String areaId);
}
