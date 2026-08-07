package com.intco.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intco.warehouse.entity.WarehouseEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface WarehouseMapper extends BaseMapper<WarehouseEntity> {
    List<Map<String, Object>> selectWarehouseDaily(@Param("warehouseId") String warehouseId);
    List<Map<String, Object>> selectLatestZones(@Param("warehouseId") String warehouseId);
    List<Map<String, Object>> selectLatestZoneByArea(@Param("areaId") String areaId);
    List<Map<String, Object>> selectAlerts(@Param("warehouseId") String warehouseId);
    List<Map<String, Object>> selectAlertsByArea(@Param("areaId") String areaId);
    List<Map<String, Object>> selectTargets();
    Map<String, Object> selectMeta(@Param("warehouseId") String warehouseId);
    List<Map<String, Object>> selectInventory(@Param("warehouseId") String warehouseId);
    List<Map<String, Object>> selectGroupedInventory(@Param("warehouseId") String warehouseId);
    Map<String, Object> selectDataCounts();

    List<Map<String, Object>> exportWarehouses();
    List<Map<String, Object>> exportInventory();
    List<Map<String, Object>> exportSkuDaily();
    List<Map<String, Object>> exportWarehouseDaily();
    List<Map<String, Object>> exportAreaSnapshots();
    List<Map<String, Object>> exportExceptions();
    List<Map<String, Object>> exportBom();
    List<Map<String, Object>> exportTargets();

    int deleteWarehouseDaily(@Param("bizDate") Object bizDate, @Param("warehouseId") Object warehouseId);
    int clearExceptionEvents();
    int clearAreaSnapshots();
    int clearWarehouseDaily();
    int clearSkuDaily();
    int clearInventory();
    int clearBom();
    int clearTargets();
    int clearWarehouses();
    int insertWarehouses(@Param("row") Object[] row);
    int insertInventory(@Param("row") Object[] row);
    int insertSkuDaily(@Param("row") Object[] row);
    int insertWarehouseDaily(@Param("row") Object[] row);
    int insertAreaSnapshots(@Param("row") Object[] row);
    int insertExceptions(@Param("row") Object[] row);
    int insertBom(@Param("row") Object[] row);
    int insertTargets(@Param("row") Object[] row);
    int insertImportJob(@Param("id") String id, @Param("fileName") String fileName, @Param("type") String type,
                        @Param("rows") int rows, @Param("startedAt") LocalDateTime startedAt,
                        @Param("finishedAt") LocalDateTime finishedAt, @Param("status") String status,
                        @Param("message") String message);
}
