package com.zhyf.report.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.report.application.ReportQueryService;
import com.zhyf.report.application.ReportRecords;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    private final ReportQueryService reportQueryService;

    public ReportController(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    @GetMapping("/overview")
    public ApiResponse<ReportRecords.ReportOverview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "14") int trendDays
    ) {
        return ApiResponse.ok(reportQueryService.overview(from, to, trendDays));
    }

    @GetMapping("/overview.csv")
    public ResponseEntity<byte[]> overviewCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "14") int trendDays
    ) {
        byte[] content = reportQueryService.exportOverviewCsv(from, to, trendDays).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("report-overview.csv", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }
}
