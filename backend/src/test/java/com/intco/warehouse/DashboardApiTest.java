package com.intco.warehouse;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void overviewExposesExecutiveSummaryAndDetails() throws Exception {
        mvc.perform(get("/api/dashboard/overview").param("range", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.healthScore", greaterThan(0)))
                .andExpect(jsonPath("$.trend", hasSize(7)))
                .andExpect(jsonPath("$.warehouseDaily", hasSize(93)))
                .andExpect(jsonPath("$.zones", hasSize(12)))
                .andExpect(jsonPath("$.alerts", hasSize(208)))
                .andExpect(jsonPath("$.targets", hasSize(7)));
    }

    @Test
    void warehouseDashboardSupportsAllThreeBoards() throws Exception {
        mvc.perform(get("/api/dashboard/warehouses/WH-RM01").param("range", "31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend", hasSize(31)))
                .andExpect(jsonPath("$.zones", hasSize(8)));
        mvc.perform(get("/api/dashboard/warehouses/WH-FG03").param("range", "31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily", hasSize(31)))
                .andExpect(jsonPath("$.zones", hasSize(2)));
        mvc.perform(get("/api/dashboard/warehouses/WH-PK04").param("range", "31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily", hasSize(31)))
                .andExpect(jsonPath("$.zones", hasSize(2)));
    }

    @Test
    void exportProvidesRealWorkbook() throws Exception {
        byte[] content = mvc.perform(get("/api/data/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            assertEquals(11, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("运营_SKU日指标"));
            assertEquals(1970, workbook.getSheet("运营_SKU日指标").getLastRowNum());
            assertNotNull(workbook.getSheet("\u5e93\u9f84\u6279\u6b21\u660e\u7ec6"));
            assertEquals(173, workbook.getSheet("\u5e93\u9f84\u6279\u6b21\u660e\u7ec6").getLastRowNum());
            assertNotNull(workbook.getSheet("\u5e93\u9f84SKU\u6c47\u603b"));
            assertEquals(40, workbook.getSheet("\u5e93\u9f84SKU\u6c47\u603b").getLastRowNum());
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void exportedWorkbookCanBeImportedAgain() throws Exception {
        byte[] exported = mvc.perform(get("/api/data/export").param("format", "xlsx"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "roundtrip.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", exported);
        mvc.perform(multipart("/api/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importType").value("FULL_WORKBOOK"))
                .andExpect(jsonPath("$.importedRows").value(2968))
                .andExpect(jsonPath("$.endDate").value("2026-07-31"));
    }

    @Test
    void dataStatusExposesInventoryAgingCounts() throws Exception {
        mvc.perform(get("/api/data/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inventoryAgeRules").value(10))
                .andExpect(jsonPath("$.inventoryAgeBatches").value(171))
                .andExpect(jsonPath("$.inventoryAgeSkus").value(38));
    }

    @Test
    void unknownZoneReturnsNotFound() throws Exception {
        mvc.perform(get("/api/zones/UNKNOWN")).andExpect(status().isNotFound());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void csvImportValidatesAndMergesDailyMetric() throws Exception {
        String csv = "日期,入库箱数,出库箱数,拣货任务,叉车任务,库存准确率,入库及时率,出库及时率,异常数,收货时长(分钟),拣货时长(分钟),平均作业时长(分钟),月台利用率,加班工时\n"
                + "2026-08-01,520,488,126,245,98.6%,95.8%,94.7%,4,41,38,38,72%,1.5\n";
        MockMultipartFile file = new MockMultipartFile("file", "daily.csv", "text/csv", csv.getBytes("UTF-8"));

        mvc.perform(multipart("/api/data/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(1))
                .andExpect(jsonPath("$.endDate").value("2026-08-01"));
    }
}
