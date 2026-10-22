package com.tcet.RestApi.controller;

import com.tcet.RestApi.dto.JobOpeningRequest;
import com.tcet.RestApi.dto.JobOpeningResponse;
import com.tcet.RestApi.service.JobOpeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobOpeningController {

    private final JobOpeningService service;

    @GetMapping
    public List<JobOpeningResponse> all(@RequestParam(required = false) Long companyId) {
        return service.findAll(companyId);
    }

    @GetMapping("/{id}")
    public JobOpeningResponse one(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobOpeningResponse create(@Valid @RequestBody JobOpeningRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public JobOpeningResponse update(@PathVariable Long id, @Valid @RequestBody JobOpeningRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
