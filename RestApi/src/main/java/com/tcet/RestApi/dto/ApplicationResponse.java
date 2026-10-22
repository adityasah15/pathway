package com.tcet.RestApi.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ApplicationResponse(
        Long id,
        Long studentId,
        String studentName,
        Long jobId,
        String jobTitle,
        Long companyId,
        String companyName,
        LocalDate appliedDate,
        String status,
        LocalDate interviewDate,
        Double offerPackageLpa,
        String notes,
        LocalDateTime updatedAt) {
}
