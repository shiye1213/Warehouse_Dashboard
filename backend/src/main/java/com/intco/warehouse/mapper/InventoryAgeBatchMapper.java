package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.InventoryAgeBatchEntity;
import java.util.List;

public interface InventoryAgeBatchMapper extends BaseMapper<InventoryAgeBatchEntity> {
    List<InventoryAgeBatchEntity> selectJoined();
}
