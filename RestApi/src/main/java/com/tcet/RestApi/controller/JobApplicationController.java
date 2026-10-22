package com.tcet.RestApi.controller;

import com.tcet.RestApi.dto.ApplicationRequest;
import com.tcet.RestApi.dto.ApplicationResponse;
import com.tcet.RestApi.service.ApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final ApplicationService service;

    @GetMapping
    public List<ApplicationResponse> all(@RequestParam(required = false) Long studentId) {
        return service.findAll(studentId);
    }

    @GetMapping("/{id}")
    public ApplicationResponse one(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody ApplicationRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody ApplicationRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate body) {
        return service.updateStatus(id, body.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    public record StatusUpdate(@NotBlank(message = "Status is required") String status) {
    }
}
