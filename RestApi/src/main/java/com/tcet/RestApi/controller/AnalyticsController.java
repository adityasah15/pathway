package com.tcet.RestApi.controller;

import com.tcet.RestApi.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController @RequestMapping("/api/analytics") @RequiredArgsConstructor
public class AnalyticsController {
    private final StudentRepository students;
    private final CompanyRepository companies;
    private final JobOpeningRepository jobs;
    private final JobApplicationRepository applications;
    private final RestClient analyticsRestClient;

    @GetMapping("/summary") public Map<String, Object> summary() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("students", students.count());
        result.put("companies", companies.count());
        result.put("jobOpenings", jobs.count());
        result.put("applications", applications.count());
        result.put("interviews", applications.countByStatusIgnoreCase("Interview"));
        result.put("offers", applications.countByStatusIgnoreCase("Offered") + applications.countByStatusIgnoreCase("Accepted"));
        result.put("accepted", applications.countByStatusIgnoreCase("Accepted"));
        result.put("rejected", applications.countByStatusIgnoreCase("Rejected"));
        return result;
    }

    @GetMapping("/dashboard")
    public Object dashboard(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer graduationYear,
            @RequestParam(required = false) String status) {
        return analyticsRestClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/dashboard");
                    if (department != null && !department.isBlank()) {
                        uriBuilder.queryParam("department", department);
                    }
                    if (graduationYear != null) {
                        uriBuilder.queryParam("graduationYear", graduationYear);
                    }
                    if (status != null && !status.isBlank()) {
                        uriBuilder.queryParam("status", status);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(Object.class);
    }
}
