package com.tcet.RestApi.repositorys;
import com.tcet.RestApi.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompanyRepository extends JpaRepository<Company, Long> { }
