package com.tcet.RestApi.service;

import com.tcet.RestApi.dto.ApplicationRequest;
import com.tcet.RestApi.dto.ApplicationResponse;
import com.tcet.RestApi.model.ApplicationStatus;
import com.tcet.RestApi.model.ApplicationStatusHistory;
import com.tcet.RestApi.model.JobApplication;
import com.tcet.RestApi.model.JobOpening;
import com.tcet.RestApi.model.Student;
import com.tcet.RestApi.repositories.ApplicationStatusHistoryRepository;
import com.tcet.RestApi.repositories.JobApplicationRepository;
import com.tcet.RestApi.repositories.JobOpeningRepository;
import com.tcet.RestApi.repositories.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private static final Set<ApplicationStatus> INTERVIEW_STAGES = Set.of(
            ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFERED, ApplicationStatus.ACCEPTED);

    private static final Set<ApplicationStatus> OFFER_STAGES = Set.of(
            ApplicationStatus.OFFERED, ApplicationStatus.ACCEPTED);

    private final JobApplicationRepository applications;
    private final StudentRepository students;
    private final JobOpeningRepository jobs;
    private final ApplicationStatusHistoryRepository history;

    public List<ApplicationResponse> findAll(Long studentId) {
        List<JobApplication> items = studentId == null
                ? applications.findAllDetailed()
                : applications.findByStudentId(studentId);
        return items.stream().map(this::toResponse).toList();
    }

    public ApplicationResponse findOne(Long id) {
        return toResponse(get(id));
    }

    @Transactional
    public ApplicationResponse create(ApplicationRequest request) {
        Student student = requireStudent(request.studentId());
        JobOpening job = requireJob(request.jobId());
        ApplicationStatus status = parseStatus(request.status());

        validateEligibility(student, job, request.appliedDate());
        validateDetails(status, request.interviewDate(), request.offerPackageLpa());

        if (applications.existsByStudent_IdAndJob_Id(student.getId(), job.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student already applied for this job");
        }

        JobApplication item = new JobApplication();
        item.setStudent(student);
        item.setJob(job);
        applyRequest(item, request, status);
        JobApplication saved = applications.save(item);
        recordHistory(saved, status, "Application created");
        return toResponse(saved);
    }

    @Transactional
    public ApplicationResponse update(Long id, ApplicationRequest request) {
        JobApplication item = get(id);
        Student student = requireStudent(request.studentId());
        JobOpening job = requireJob(request.jobId());
        ApplicationStatus nextStatus = parseStatus(request.status());
        ApplicationStatus currentStatus = parseStatus(item.getStatus());

        validateTransition(currentStatus, nextStatus);
        validateEligibility(student, job, request.appliedDate());
        validateDetails(nextStatus, request.interviewDate(), request.offerPackageLpa());

        if (applications.existsByStudent_IdAndJob_IdAndIdNot(student.getId(), job.getId(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student already applied for this job");
        }

        item.setStudent(student);
        item.setJob(job);
        applyRequest(item, request, nextStatus);
        JobApplication saved = applications.save(item);

        if (nextStatus != currentStatus) {
            recordHistory(saved, nextStatus, "Application edited");
        }
        return toResponse(saved);
    }

    @Transactional
    public ApplicationResponse updateStatus(Long id, String status) {
        ApplicationStatus nextStatus = parseStatus(status);
        JobApplication item = get(id);
        ApplicationStatus currentStatus = parseStatus(item.getStatus());

        validateTransition(currentStatus, nextStatus);
        validateDetails(nextStatus, item.getInterviewDate(), item.getOfferPackageLpa());

        item.setStatus(nextStatus.label());
        JobApplication saved = applications.save(item);
        if (nextStatus != currentStatus) {
            recordHistory(saved, nextStatus, "Status updated");
        }
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        applications.delete(get(id));
    }

    private JobApplication get(Long id) {
        return applications.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private Student requireStudent(Long id) {
        return students.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing student"));
    }

    private JobOpening requireJob(Long id) {
        return jobs.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing job opening"));
    }

    private void applyRequest(JobApplication item, ApplicationRequest request, ApplicationStatus status) {
        item.setAppliedDate(request.appliedDate());
        item.setStatus(status.label());
        item.setInterviewDate(request.interviewDate());
        item.setOfferPackageLpa(request.offerPackageLpa());
        item.setNotes(request.notes());
    }

    private ApplicationStatus parseStatus(String value) {
        try {
            return ApplicationStatus.fromLabel(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private void validateTransition(ApplicationStatus current, ApplicationStatus next) {
        if (!current.canTransitionTo(next)) {
            String allowed = current.allowedNext().stream()
                    .map(ApplicationStatus::label)
                    .sorted()
                    .collect(Collectors.joining(", "));
            String message = allowed.isEmpty()
                    ? "An application in " + current.label() + " is closed and cannot change status."
                    : "Cannot move from " + current.label() + " to " + next.label() + ". Allowed next: " + allowed + ".";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void validateDetails(ApplicationStatus status, LocalDate interviewDate, Double offerPackage) {
        if (interviewDate != null && !INTERVIEW_STAGES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Interview date is only valid from Shortlisted onward");
        }
        if (offerPackage != null && !OFFER_STAGES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Offer package is only valid for Offered or Accepted applications");
        }
    }

    private void validateEligibility(Student student, JobOpening job, LocalDate appliedDate) {
        if (job.getDeadline() != null && appliedDate != null && appliedDate.isAfter(job.getDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The application deadline has passed");
        }
        if (student.getCgpa() != null && job.getMinCgpa() != null && student.getCgpa() < job.getMinCgpa()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Student CGPA " + student.getCgpa() + " is below the job minimum of " + job.getMinCgpa());
        }
    }

    private void recordHistory(JobApplication application, ApplicationStatus status, String note) {
        history.save(new ApplicationStatusHistory(
                application,
                status.label(),
                LocalDateTime.now(),
                currentUsername(),
                note));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }

    private ApplicationResponse toResponse(JobApplication item) {
        Student student = item.getStudent();
        JobOpening job = item.getJob();
        return new ApplicationResponse(
                item.getId(),
                student.getId(),
                student.getFullName(),
                job.getId(),
                job.getJobTitle(),
                job.getCompany().getId(),
                job.getCompany().getCompanyName(),
                item.getAppliedDate(),
                item.getStatus(),
                item.getInterviewDate(),
                item.getOfferPackageLpa(),
                item.getNotes(),
                item.getUpdatedAt());
    }
}
