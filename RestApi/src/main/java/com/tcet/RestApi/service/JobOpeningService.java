package com.tcet.RestApi.service;

import com.tcet.RestApi.dto.JobOpeningRequest;
import com.tcet.RestApi.dto.JobOpeningResponse;
import com.tcet.RestApi.model.Company;
import com.tcet.RestApi.model.JobOpening;
import com.tcet.RestApi.repositories.CompanyRepository;
import com.tcet.RestApi.repositories.JobOpeningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobOpeningService {

    private final JobOpeningRepository jobs;
    private final CompanyRepository companies;

    public List<JobOpeningResponse> findAll(Long companyId) {
        List<JobOpening> items = companyId == null
                ? jobs.findAllDetailed()
                : jobs.findByCompanyId(companyId);
        return items.stream().map(this::toResponse).toList();
    }

    public JobOpeningResponse findOne(Long id) {
        return toResponse(get(id));
    }

    @Transactional
    public JobOpeningResponse create(JobOpeningRequest request) {
        JobOpening item = new JobOpening();
        apply(item, request);
        return toResponse(jobs.save(item));
    }

    @Transactional
    public JobOpeningResponse update(Long id, JobOpeningRequest request) {
        JobOpening item = get(id);
        apply(item, request);
        return toResponse(jobs.save(item));
    }

    @Transactional
    public void delete(Long id) {
        jobs.delete(get(id));
    }

    private JobOpening get(Long id) {
        return jobs.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job opening not found"));
    }

    private void apply(JobOpening item, JobOpeningRequest request) {
        Company company = companies.findById(request.companyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing company"));
        item.setCompany(company);
        item.setJobTitle(request.jobTitle());
        item.setJobType(request.jobType());
        item.setPackageLpa(request.packageLpa());
        item.setMinCgpa(request.minCgpa());
        item.setPostedDate(request.postedDate());
        item.setDeadline(request.deadline());
    }

    private JobOpeningResponse toResponse(JobOpening item) {
        Company company = item.getCompany();
        return new JobOpeningResponse(
                item.getId(),
                company.getId(),
                company.getCompanyName(),
                item.getJobTitle(),
                item.getJobType(),
                item.getPackageLpa(),
                item.getMinCgpa(),
                item.getPostedDate(),
                item.getDeadline());
    }
}
