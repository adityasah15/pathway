package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.Student;
import com.tcet.RestApi.repositorys.StudentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/students") @RequiredArgsConstructor
public class StudentController {
    private final StudentRepository repository;
    @GetMapping public List<Student> all() { return repository.findAll(); }
    @GetMapping("/{id}") public Student one(@PathVariable Long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found")); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Student create(@Valid @RequestBody Student item) {
        if (repository.existsByRollNo(item.getRollNo())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Roll number already exists");
        item.setId(null); return repository.save(item);
    }
    @PutMapping("/{id}") public Student update(@PathVariable Long id, @Valid @RequestBody Student item) {
        if (repository.existsByRollNoAndIdNot(item.getRollNo(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Roll number already exists");
        Student old = one(id); old.setRollNo(item.getRollNo()); old.setFullName(item.getFullName()); old.setEmail(item.getEmail()); old.setDepartment(item.getDepartment()); old.setCgpa(item.getCgpa()); old.setGraduationYear(item.getGraduationYear()); return repository.save(old);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { repository.delete(one(id)); }
}
