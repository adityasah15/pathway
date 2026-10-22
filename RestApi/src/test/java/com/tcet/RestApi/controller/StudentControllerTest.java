package com.tcet.RestApi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcet.RestApi.model.Student;
import com.tcet.RestApi.repositorys.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", roles = "ADMIN")
class StudentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StudentRepository students;

    @Autowired
    private ObjectMapper json;

    private Student seed(String rollNo) {
        Student student = new Student();
        student.setRollNo(rollNo);
        student.setFullName("Seed Student");
        student.setDepartment("Information Technology");
        return students.save(student);
    }

    @Test
    void createsStudent() throws Exception {
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rollNo":"IT010","fullName":"New Student","email":"new@example.com","department":"IT","cgpa":8.4,"graduationYear":2027}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rollNo").value("IT010"));

        assertThat(students.existsByRollNo("IT010")).isTrue();
    }

    @Test
    void rejectsDuplicateRollNumber() throws Exception {
        seed("IT001");

        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rollNo":"IT001","fullName":"Duplicate","department":"IT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Roll number already exists"));
    }

    @Test
    void rejectsDuplicateRollNumberOnUpdate() throws Exception {
        seed("IT001");
        Student second = seed("IT002");

        mvc.perform(put("/api/students/{id}", second.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rollNo":"IT001","fullName":"Renamed","department":"IT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Roll number already exists"));
    }

    @Test
    void returnsFriendlyNotFound() throws Exception {
        mvc.perform(get("/api/students/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Student not found"))
                .andExpect(jsonPath("$.path").value("/api/students/999999"));
    }

    @Test
    void returnsFieldErrorsForInvalidBody() throws Exception {
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.rollNo").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    @Test
    void deletesStudent() throws Exception {
        Student student = seed("IT020");

        mvc.perform(delete("/api/students/{id}", student.getId()))
                .andExpect(status().isNoContent());

        assertThat(students.findById(student.getId())).isEmpty();
    }
}
