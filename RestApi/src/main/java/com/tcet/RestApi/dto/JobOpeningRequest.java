package com.tcet.RestApi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record JobOpeningRequest(
        @NotNull(message = "Select an existing company") Long companyId,
        @NotBlank(message = "Job title is required") String jobTitle,
        @NotBlank(message = "Job type is required") String jobType,
        @DecimalMin(value = "0.0", message = "Package cannot be negative") Double packageLpa,
        @DecimalMin(value = "0.0", message = "Minimum CGPA cannot be negative")
        @DecimalMax(value = "10.0", message = "Minimum CGPA cannot exceed 10") Double minCgpa,
        LocalDate postedDate,
        LocalDate deadline) {
}
