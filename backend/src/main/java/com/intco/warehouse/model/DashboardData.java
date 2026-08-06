package com.intco.warehouse.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardData {
    private Map<String, Object> meta = new LinkedHashMap<>();
    private List<DailyMetric> daily = new ArrayList<>();
    private List<WarehouseDailyMetric> warehouseDaily = new ArrayList<>();
    private List<Zone> zones = new ArrayList<>();
    private List<Alert> alerts = new ArrayList<>();
    private List<Target> targets = new ArrayList<>();
    private List<Forklift> forklifts = new ArrayList<>();

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
    public List<DailyMetric> getDaily() { return daily; }
    public void setDaily(List<DailyMetric> daily) { this.daily = daily; }
    public List<WarehouseDailyMetric> getWarehouseDaily() { return warehouseDaily; }
    public void setWarehouseDaily(List<WarehouseDailyMetric> warehouseDaily) { this.warehouseDaily = warehouseDaily; }
    public List<Zone> getZones() { return zones; }
    public void setZones(List<Zone> zones) { this.zones = zones; }
    public List<Alert> getAlerts() { return alerts; }
    public void setAlerts(List<Alert> alerts) { this.alerts = alerts; }
    public List<Target> getTargets() { return targets; }
    public void setTargets(List<Target> targets) { this.targets = targets; }
    public List<Forklift> getForklifts() { return forklifts; }
    public void setForklifts(List<Forklift> forklifts) { this.forklifts = forklifts; }

    public static class DailyMetric {
        private String date;
        private int inbound;
        private int outbound;
        private int picking;
        private int forkliftTasks;
        private double inventoryAccuracy;
        private double receivingTimely;
        private double deliveryTimely;
        private int exceptions;
        private double receiptMinutes;
        private double pickingMinutes;
        private double averageDuration;
        private double dockUtilization;
        private double overtimeHours;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public int getInbound() { return inbound; }
        public void setInbound(int inbound) { this.inbound = inbound; }
        public int getOutbound() { return outbound; }
        public void setOutbound(int outbound) { this.outbound = outbound; }
        public int getPicking() { return picking; }
        public void setPicking(int picking) { this.picking = picking; }
        public int getForkliftTasks() { return forkliftTasks; }
        public void setForkliftTasks(int forkliftTasks) { this.forkliftTasks = forkliftTasks; }
        public double getInventoryAccuracy() { return inventoryAccuracy; }
        public void setInventoryAccuracy(double inventoryAccuracy) { this.inventoryAccuracy = inventoryAccuracy; }
        public double getReceivingTimely() { return receivingTimely; }
        public void setReceivingTimely(double receivingTimely) { this.receivingTimely = receivingTimely; }
        public double getDeliveryTimely() { return deliveryTimely; }
        public void setDeliveryTimely(double deliveryTimely) { this.deliveryTimely = deliveryTimely; }
        public int getExceptions() { return exceptions; }
        public void setExceptions(int exceptions) { this.exceptions = exceptions; }
        public double getReceiptMinutes() { return receiptMinutes; }
        public void setReceiptMinutes(double receiptMinutes) { this.receiptMinutes = receiptMinutes; }
        public double getPickingMinutes() { return pickingMinutes; }
        public void setPickingMinutes(double pickingMinutes) { this.pickingMinutes = pickingMinutes; }
        public double getAverageDuration() { return averageDuration; }
        public void setAverageDuration(double averageDuration) { this.averageDuration = averageDuration; }
        public double getDockUtilization() { return dockUtilization; }
        public void setDockUtilization(double dockUtilization) { this.dockUtilization = dockUtilization; }
        public double getOvertimeHours() { return overtimeHours; }
        public void setOvertimeHours(double overtimeHours) { this.overtimeHours = overtimeHours; }
    }

    public static class WarehouseDailyMetric extends DailyMetric {
        private String warehouseId;
        private String warehouseName;
        private String warehouseType;
        private int inboundOrders;
        private int outboundOrders;
        private double rawInboundTon;
        private double rawOutboundTon;
        private int finishedInboundCarton;
        private int finishedOutboundCarton;
        private int packagingInboundPiece;
        private int packagingOutboundPiece;

        public String getWarehouseId() { return warehouseId; }
        public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        public String getWarehouseName() { return warehouseName; }
        public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
        public String getWarehouseType() { return warehouseType; }
        public void setWarehouseType(String warehouseType) { this.warehouseType = warehouseType; }
        public int getInboundOrders() { return inboundOrders; }
        public void setInboundOrders(int inboundOrders) { this.inboundOrders = inboundOrders; }
        public int getOutboundOrders() { return outboundOrders; }
        public void setOutboundOrders(int outboundOrders) { this.outboundOrders = outboundOrders; }
        public double getRawInboundTon() { return rawInboundTon; }
        public void setRawInboundTon(double rawInboundTon) { this.rawInboundTon = rawInboundTon; }
        public double getRawOutboundTon() { return rawOutboundTon; }
        public void setRawOutboundTon(double rawOutboundTon) { this.rawOutboundTon = rawOutboundTon; }
        public int getFinishedInboundCarton() { return finishedInboundCarton; }
        public void setFinishedInboundCarton(int finishedInboundCarton) { this.finishedInboundCarton = finishedInboundCarton; }
        public int getFinishedOutboundCarton() { return finishedOutboundCarton; }
        public void setFinishedOutboundCarton(int finishedOutboundCarton) { this.finishedOutboundCarton = finishedOutboundCarton; }
        public int getPackagingInboundPiece() { return packagingInboundPiece; }
        public void setPackagingInboundPiece(int packagingInboundPiece) { this.packagingInboundPiece = packagingInboundPiece; }
        public int getPackagingOutboundPiece() { return packagingOutboundPiece; }
        public void setPackagingOutboundPiece(int packagingOutboundPiece) { this.packagingOutboundPiece = packagingOutboundPiece; }
    }

    public static class Zone {
        private String snapshotDate;
        private String warehouse;
        private String code;
        private String name;
        private int capacity;
        private int occupied;
        private int available;
        private double occupancy;
        private int materialTypes;
        private int abnormal;
        private int frozen;
        private String manager;
        private String status;

        public String getSnapshotDate() { return snapshotDate; }
        public void setSnapshotDate(String snapshotDate) { this.snapshotDate = snapshotDate; }
        public String getWarehouse() { return warehouse; }
        public void setWarehouse(String warehouse) { this.warehouse = warehouse; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getOccupied() { return occupied; }
        public void setOccupied(int occupied) { this.occupied = occupied; }
        public int getAvailable() { return available; }
        public void setAvailable(int available) { this.available = available; }
        public double getOccupancy() { return occupancy; }
        public void setOccupancy(double occupancy) { this.occupancy = occupancy; }
        public int getMaterialTypes() { return materialTypes; }
        public void setMaterialTypes(int materialTypes) { this.materialTypes = materialTypes; }
        public int getAbnormal() { return abnormal; }
        public void setAbnormal(int abnormal) { this.abnormal = abnormal; }
        public int getFrozen() { return frozen; }
        public void setFrozen(int frozen) { this.frozen = frozen; }
        public String getManager() { return manager; }
        public void setManager(String manager) { this.manager = manager; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class Alert {
        private String id;
        private String date;
        private String time;
        private String type;
        private String title;
        private String warehouse;
        private String zone;
        private String zoneCode;
        private String severity;
        private String status;
        private String owner;
        private int responseMinutes;
        private double slaHours;
        private double durationHours;
        private boolean slaBreached;
        private String closedAt;
        private String description;
        private String recommendation;
        private String material;
        private String project;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getWarehouse() { return warehouse; }
        public void setWarehouse(String warehouse) { this.warehouse = warehouse; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public String getZoneCode() { return zoneCode; }
        public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
        public int getResponseMinutes() { return responseMinutes; }
        public void setResponseMinutes(int responseMinutes) { this.responseMinutes = responseMinutes; }
        public double getSlaHours() { return slaHours; }
        public void setSlaHours(double slaHours) { this.slaHours = slaHours; }
        public double getDurationHours() { return durationHours; }
        public void setDurationHours(double durationHours) { this.durationHours = durationHours; }
        public boolean isSlaBreached() { return slaBreached; }
        public void setSlaBreached(boolean slaBreached) { this.slaBreached = slaBreached; }
        public String getClosedAt() { return closedAt; }
        public void setClosedAt(String closedAt) { this.closedAt = closedAt; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public String getProject() { return project; }
        public void setProject(String project) { this.project = project; }
    }

    public static class Target {
        private String key;
        private String name;
        private double target;
        private String unit;
        private String rule;
        private String definition;
        private String source;
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getTarget() { return target; }
        public void setTarget(double target) { this.target = target; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getRule() { return rule; }
        public void setRule(String rule) { this.rule = rule; }
        public String getDefinition() { return definition; }
        public void setDefinition(String definition) { this.definition = definition; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class Forklift {
        private String id;
        private String status;
        private String task;
        private String zone;
        private int tasks;
        private int peakTasks;
        private double load;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public int getTasks() { return tasks; }
        public void setTasks(int tasks) { this.tasks = tasks; }
        public int getPeakTasks() { return peakTasks; }
        public void setPeakTasks(int peakTasks) { this.peakTasks = peakTasks; }
        public double getLoad() { return load; }
        public void setLoad(double load) { this.load = load; }
    }
}
