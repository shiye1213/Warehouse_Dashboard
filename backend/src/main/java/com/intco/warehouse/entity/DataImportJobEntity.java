package com.intco.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("data_import_job")
public class DataImportJobEntity {
    @TableId(value = "import_id", type = IdType.INPUT)
    private String importId;
    private String fileName;
    private String importType;
    private Integer importedRows;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private String message;
}
