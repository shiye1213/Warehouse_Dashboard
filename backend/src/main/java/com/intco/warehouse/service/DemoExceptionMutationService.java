package com.intco.warehouse.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.intco.warehouse.entity.ExceptionEventEntity;
import com.intco.warehouse.entity.WarehouseEntity;
import com.intco.warehouse.mapper.ExceptionEventMapper;
import com.intco.warehouse.mapper.WarehouseMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoExceptionMutationService {
    public static final String DEMO_EVENT_ID = "DEMO-AUTO-EXCEPTION";

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoExceptionMutationService.class);

    private final ExceptionEventMapper exceptionEventMapper;
    private final WarehouseMapper warehouseMapper;

    public DemoExceptionMutationService(ExceptionEventMapper exceptionEventMapper, WarehouseMapper warehouseMapper) {
        this.exceptionEventMapper = exceptionEventMapper;
        this.warehouseMapper = warehouseMapper;
    }

    @Transactional
    public MutationAction toggle() {
        if (exceptionEventMapper.selectById(DEMO_EVENT_ID) != null) {
            exceptionEventMapper.deleteById(DEMO_EVENT_ID);
            LOGGER.info("Deleted scheduled demo exception {}", DEMO_EVENT_ID);
            return MutationAction.DELETED;
        }

        List<WarehouseEntity> warehouses = warehouseMapper.selectList(
                Wrappers.lambdaQuery(WarehouseEntity.class).orderByAsc(WarehouseEntity::getWarehouseId));
        if (warehouses.isEmpty()) {
            LOGGER.warn("Skipped scheduled demo exception creation because no warehouse exists");
            return MutationAction.SKIPPED;
        }

        LocalDateTime now = LocalDateTime.now();
        ExceptionEventEntity event = new ExceptionEventEntity();
        event.setEventId(DEMO_EVENT_ID);
        event.setEventTime(now);
        event.setEventType("自动演示异常");
        event.setWarehouseId(warehouses.get(0).getWarehouseId());
        event.setAreaName("定时任务演示区");
        event.setSeverity("重要");
        event.setHandlingStatus("处理中");
        event.setOwner("系统演示任务");
        event.setResponseMinutes(0);
        event.setSlaHours(BigDecimal.valueOf(2));
        event.setDeadlineTime(now.plusHours(2));
        event.setDurationMinutes(0);
        event.setIsSlaBreached(false);
        event.setRootCause("系统每五分钟自动生成，用于演示数据库新增与删除");
        event.setActionTaken("无需人工处理，下一次任务将自动删除");
        event.setRemark("DEMO_AUTO_MUTATION");
        exceptionEventMapper.insert(event);
        LOGGER.info("Created scheduled demo exception {} for warehouse {}", DEMO_EVENT_ID, event.getWarehouseId());
        return MutationAction.CREATED;
    }

    public enum MutationAction {
        CREATED,
        DELETED,
        SKIPPED
    }
}
