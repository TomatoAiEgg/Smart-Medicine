package com.zhyf.report.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.report.infrastructure.ReportQueryRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ReportQueryService {

    private static final int DEFAULT_TREND_DAYS = 14;
    private static final int MAX_TREND_DAYS = 60;

    private final ReportQueryRepository repository;

    public ReportQueryService(ReportQueryRepository repository) {
        this.repository = repository;
    }

    public ReportRecords.ReportOverview overview(Instant from, Instant to, int trendDays) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException("REPORT_INVALID_TIME_RANGE", "From time must be before to time");
        }
        return repository.loadOverview(from, to, normalizeTrendDays(trendDays));
    }

    private int normalizeTrendDays(int trendDays) {
        if (trendDays <= 0) {
            return DEFAULT_TREND_DAYS;
        }
        return Math.min(trendDays, MAX_TREND_DAYS);
    }
}
