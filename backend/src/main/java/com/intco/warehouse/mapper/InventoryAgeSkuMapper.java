package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.InventoryAgeSkuEntity;
import java.util.List;

public interface InventoryAgeSkuMapper extends BaseMapper<InventoryAgeSkuEntity> {
    List<InventoryAgeSkuEntity> selectJoined();
}
