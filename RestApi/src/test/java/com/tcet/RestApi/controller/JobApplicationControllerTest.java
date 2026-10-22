package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.Company;
import com.tcet.RestApi.model.JobApplication;
import com.tcet.RestApi.model.JobOpening;
import com.tcet.RestApi.model.Student;
import com.tcet.RestApi.repositories.CompanyRepository;
import com.tcet.RestApi.repositories.JobApplicationRepository;
import com.tcet.RestApi.repositories.JobOpeningRepository;
import com.tcet.RestApi.repositories.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", roles = "ADMIN")
class JobApplicationControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository students;
    @Autowired private CompanyRepository companies;
    @Autowired private JobOpeningRepository jobs;
    @Autowired private JobApplicationRepository applications;
    @Autowired private com.tcet.RestApi.repositories.ApplicationStatusHistoryRepository history;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

    private Student student(String rollNo) {
        return student(rollNo, 8.0);
    }

    private Student student(String rollNo, double cgpa) {
        Student item = new Student();
        item.setRollNo(rollNo);
        item.setFullName("Student " + rollNo);
        item.setDepartment("Information Technology");
        item.setCgpa(cgpa);
        return students.save(item);
    }

    private JobOpening job(String title) {
        return job(title, LocalDate.now().plusDays(30));
    }

    private JobOpening job(String title, LocalDate deadline) {
        Company company = new Company();
        company.setCompanyName("Company " + title);
        company = companies.save(company);

        JobOpening item = new JobOpening();
        item.setCompany(company);
        item.setJobTitle(title);
        item.setJobType("Full-time");
        item.setMinCgpa(7.0);
        item.setDeadline(deadline);
        return jobs.save(item);
    }

    private String payload(Long studentId, Long jobId, String status) {
        return """
                {"studentId":%d,"jobId":%d,"appliedDate":"2026-10-01","status":"%s"}
                """.formatted(studentId, jobId, status);
    }

    @Test
    void createsApplicationAndUpdatesStatus() throws Exception {
        Student student = student("IT001");
        JobOpening job = job("Java Developer");

        String response = mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(student.getId(), job.getId(), "Applied")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Applied"))
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(response).get("id").asLong();

        mvc.perform(patch("/api/applications/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Shortlisted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Shortlisted"));
    }

    @Test
    void rejectsDuplicateApplication() throws Exception {
        Student student = student("IT002");
        JobOpening job = job("Data Analyst");

        applications.save(application(student.getId(), job.getId(), "Applied"));

        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(student.getId(), job.getId(), "Applied")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This student already applied for this job"));
    }

    @Test
    void rejectsDuplicatePairOnUpdate() throws Exception {
        Student first = student("IT003");
        Student second = student("IT004");
        JobOpening job = job("Backend Engineer");

        applications.save(application(first.getId(), job.getId(), "Applied"));
        JobApplication other = applications.save(application(second.getId(), job.getId(), "Applied"));

        mvc.perform(put("/api/applications/{id}", other.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(first.getId(), job.getId(), "Applied")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This student already applied for this job"));
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        Student student = student("IT005");
        JobOpening job = job("QA Engineer");

        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(student.getId(), job.getId(), "Hired")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status")));
    }

    @Test
    void rejectsEmptyStatusUpdate() throws Exception {
        Student student = student("IT006");
        JobOpening job = job("DevOps Engineer");
        JobApplication saved = applications.save(application(student.getId(), job.getId(), "Applied"));

        mvc.perform(patch("/api/applications/{id}/status", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("Status is required"));
    }

    @Test
    void rejectsIllegalStatusJump() throws Exception {
        Student student = student("IT007");
        JobOpening job = job("Architect");
        JobApplication saved = applications.save(application(student.getId(), job.getId(), "Applied"));

        mvc.perform(patch("/api/applications/{id}/status", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Offered\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Cannot move from Applied to Offered")));
    }

    @Test
    void rejectsStudentBelowMinimumCgpa() throws Exception {
        Student student = student("IT008", 6.0);
        JobOpening job = job("High Bar");

        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(student.getId(), job.getId(), "Applied")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("below the job minimum")));
    }

    @Test
    void rejectsApplicationAfterDeadline() throws Exception {
        Student student = student("IT009");
        JobOpening job = job("Closed Role", LocalDate.of(2026, 9, 30));

        mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(student.getId(), job.getId(), "Applied")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The application deadline has passed"));
    }

    @Test
    void recordsStatusHistoryForEveryChange() throws Exception {
        Student student = student("IT011");
        JobOpening job = job("History Role");

        String created = mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(student.getId(), job.getId(), "Applied")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(patch("/api/applications/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Shortlisted\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/applications/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Interview\"}"))
                .andExpect(status().isOk());

        List<String> statuses = history.findByApplication_IdOrderByChangedAtAscIdAsc(id).stream()
                .map(com.tcet.RestApi.model.ApplicationStatusHistory::getStatus)
                .toList();
        org.assertj.core.api.Assertions.assertThat(statuses)
                .containsExactly("Applied", "Shortlisted", "Interview");
    }

    private JobApplication application(Long studentId, Long jobId, String status) {
        JobApplication item = new JobApplication();
        item.setStudent(students.findById(studentId).orElseThrow());
        item.setJob(jobs.findById(jobId).orElseThrow());
        item.setAppliedDate(LocalDate.of(2026, 10, 1));
        item.setStatus(status);
        return item;
    }
}
