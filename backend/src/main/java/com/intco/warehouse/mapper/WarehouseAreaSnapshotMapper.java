package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.WarehouseAreaSnapshotEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WarehouseAreaSnapshotMapper extends BaseMapper<WarehouseAreaSnapshotEntity> {
    List<WarehouseAreaSnapshotEntity> selectJoined(@Param("warehouseId") String warehouseId);
}
