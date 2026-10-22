package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.ApplicationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private static final List<String> JOB_TYPES = List.of("Full-time", "Internship", "Part-time", "Contract");

    @GetMapping
    public Map<String, Object> meta() {
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            transitions.put(status.label(), status.allowedNext().stream()
                    .map(ApplicationStatus::label)
                    .sorted()
                    .toList());
        }

        return Map.of(
                "statuses", new ArrayList<>(ApplicationStatus.labels()),
                "jobTypes", JOB_TYPES,
                "transitions", transitions);
    }
}
