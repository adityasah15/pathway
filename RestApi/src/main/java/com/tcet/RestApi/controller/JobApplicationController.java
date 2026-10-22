package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.JobApplication;
import com.tcet.RestApi.repositories.JobApplicationRepository;
import com.tcet.RestApi.repositories.JobOpeningRepository;
import com.tcet.RestApi.repositories.StudentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;

@RestController @RequestMapping("/api/applications") @RequiredArgsConstructor
public class JobApplicationController {
    private final JobApplicationRepository applications;
    private final StudentRepository students;
    private final JobOpeningRepository jobs;
    private static final Set<String> STATUSES = Set.of("Applied", "Shortlisted", "Interview", "Offered", "Accepted", "Rejected", "Withdrawn");
    @GetMapping public List<JobApplication> all(@RequestParam(required=false) Long studentId) { return studentId == null ? applications.findAll() : applications.findByStudentId(studentId); }
    @GetMapping("/{id}") public JobApplication one(@PathVariable Long id) { return applications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public JobApplication create(@Valid @RequestBody JobApplication item) {
        checkReferences(item); if (applications.existsByStudentIdAndJobId(item.getStudentId(), item.getJobId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This student already applied for this job");
        validateStatus(item.getStatus()); item.setId(null); return applications.save(item);
    }
    @PutMapping("/{id}") public JobApplication update(@PathVariable Long id, @Valid @RequestBody JobApplication item) {
        checkReferences(item); validateStatus(item.getStatus());
        if (applications.existsByStudentIdAndJobIdAndIdNot(item.getStudentId(), item.getJobId(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This student already applied for this job");
        JobApplication old=one(id); old.setStudentId(item.getStudentId()); old.setJobId(item.getJobId()); old.setAppliedDate(item.getAppliedDate()); old.setStatus(item.getStatus()); old.setInterviewDate(item.getInterviewDate()); old.setOfferPackageLpa(item.getOfferPackageLpa()); old.setNotes(item.getNotes()); return applications.save(old);
    }
    @PatchMapping("/{id}/status") public JobApplication updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdate body) { validateStatus(body.status()); JobApplication item=one(id); item.setStatus(body.status()); return applications.save(item); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { applications.delete(one(id)); }
    private void checkReferences(JobApplication item) { if (!students.existsById(item.getStudentId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing student"); if (!jobs.existsById(item.getJobId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing job opening"); }
    private void validateStatus(String status) { if (status == null || !STATUSES.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Use: " + STATUSES); }
    public record StatusUpdate(@NotBlank(message = "Status is required") String status) {}
}
