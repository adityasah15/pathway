package com.tcet.RestApi.dto;

import java.time.LocalDate;

public record JobOpeningResponse(
        Long id,
        Long companyId,
        String companyName,
        String jobTitle,
        String jobType,
        Double packageLpa,
        Double minCgpa,
        LocalDate postedDate,
        LocalDate deadline) {
}
