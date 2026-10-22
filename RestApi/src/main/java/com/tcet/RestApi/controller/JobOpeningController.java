package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.JobOpening;
import com.tcet.RestApi.repositorys.CompanyRepository;
import com.tcet.RestApi.repositorys.JobOpeningRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/jobs") @RequiredArgsConstructor
public class JobOpeningController {
    private final JobOpeningRepository jobs;
    private final CompanyRepository companies;
    @GetMapping public List<JobOpening> all(@RequestParam(required=false) Long companyId) { return companyId == null ? jobs.findAll() : jobs.findByCompanyId(companyId); }
    @GetMapping("/{id}") public JobOpening one(@PathVariable Long id) { return jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job opening not found")); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public JobOpening create(@Valid @RequestBody JobOpening item) { checkCompany(item.getCompanyId()); item.setId(null); return jobs.save(item); }
    @PutMapping("/{id}") public JobOpening update(@PathVariable Long id, @Valid @RequestBody JobOpening item) { checkCompany(item.getCompanyId()); JobOpening old=one(id); old.setCompanyId(item.getCompanyId()); old.setJobTitle(item.getJobTitle()); old.setJobType(item.getJobType()); old.setPackageLpa(item.getPackageLpa()); old.setMinCgpa(item.getMinCgpa()); old.setPostedDate(item.getPostedDate()); old.setDeadline(item.getDeadline()); return jobs.save(old); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { jobs.delete(one(id)); }
    private void checkCompany(Long id) { if (!companies.existsById(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing company"); }
}
