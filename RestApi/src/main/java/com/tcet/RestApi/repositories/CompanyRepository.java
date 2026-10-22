package com.tcet.RestApi.repositories;
import com.tcet.RestApi.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByCompanyNameIgnoreCase(String companyName);
    boolean existsByCompanyNameIgnoreCaseAndIdNot(String companyName, Long id);
}
