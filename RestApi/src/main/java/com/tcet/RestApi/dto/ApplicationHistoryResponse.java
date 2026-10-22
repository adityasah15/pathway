package com.tcet.RestApi.dto;

import java.time.LocalDateTime;

public record ApplicationHistoryResponse(
        Long id,
        Long applicationId,
        String status,
        LocalDateTime changedAt,
        String changedBy,
        String note) {
}
