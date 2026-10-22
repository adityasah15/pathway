package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.Company;
import com.tcet.RestApi.model.JobApplication;
import com.tcet.RestApi.model.JobOpening;
import com.tcet.RestApi.model.Student;
import com.tcet.RestApi.repositorys.CompanyRepository;
import com.tcet.RestApi.repositorys.JobApplicationRepository;
import com.tcet.RestApi.repositorys.JobOpeningRepository;
import com.tcet.RestApi.repositorys.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
class JobApplicationControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository students;
    @Autowired private CompanyRepository companies;
    @Autowired private JobOpeningRepository jobs;
    @Autowired private JobApplicationRepository applications;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

    private Student student(String rollNo) {
        Student item = new Student();
        item.setRollNo(rollNo);
        item.setFullName("Student " + rollNo);
        item.setDepartment("Information Technology");
        item.setCgpa(8.0);
        return students.save(item);
    }

    private JobOpening job(String title) {
        Company company = new Company();
        company.setCompanyName("Company " + title);
        company = companies.save(company);

        JobOpening item = new JobOpening();
        item.setCompanyId(company.getId());
        item.setJobTitle(title);
        item.setJobType("Full-time");
        item.setMinCgpa(7.0);
        item.setDeadline(LocalDate.now().plusDays(30));
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
                        .content("{\"status\":\"Interview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Interview"));
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

    private JobApplication application(Long studentId, Long jobId, String status) {
        JobApplication item = new JobApplication();
        item.setStudentId(studentId);
        item.setJobId(jobId);
        item.setAppliedDate(LocalDate.of(2026, 10, 1));
        item.setStatus(status);
        return item;
    }
}
