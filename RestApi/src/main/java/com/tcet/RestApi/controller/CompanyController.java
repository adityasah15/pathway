package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.Company;
import com.tcet.RestApi.repositorys.CompanyRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/companies") @RequiredArgsConstructor
public class CompanyController {
    private final CompanyRepository repository;
    @GetMapping public List<Company> all() { return repository.findAll(); }
    @GetMapping("/{id}") public Company one(@PathVariable Long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found")); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Company create(@Valid @RequestBody Company item) { if (repository.existsByCompanyNameIgnoreCase(item.getCompanyName())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Company name already exists"); item.setId(null); return repository.save(item); }
    @PutMapping("/{id}") public Company update(@PathVariable Long id, @Valid @RequestBody Company item) { if (repository.existsByCompanyNameIgnoreCaseAndIdNot(item.getCompanyName(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Company name already exists"); Company old=one(id); old.setCompanyName(item.getCompanyName()); old.setIndustry(item.getIndustry()); old.setLocation(item.getLocation()); old.setWebsite(item.getWebsite()); return repository.save(old); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { repository.delete(one(id)); }
}
