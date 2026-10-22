package com.tcet.RestApi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ApplicationRequest(
        @NotNull(message = "Select an existing student") Long studentId,
        @NotNull(message = "Select an existing job opening") Long jobId,
        @NotNull(message = "Applied date is required") LocalDate appliedDate,
        @NotBlank(message = "Status is required") String status,
        LocalDate interviewDate,
        @DecimalMin(value = "0.0", message = "Offer package cannot be negative") Double offerPackageLpa,
        @Size(max = 500, message = "Notes cannot exceed 500 characters") String notes) {
}
