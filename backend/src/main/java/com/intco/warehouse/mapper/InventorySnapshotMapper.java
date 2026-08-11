package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.InventorySnapshotEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InventorySnapshotMapper extends BaseMapper<InventorySnapshotEntity> {
    List<InventorySnapshotEntity> selectJoined(@Param("warehouseId") String warehouseId);
}
