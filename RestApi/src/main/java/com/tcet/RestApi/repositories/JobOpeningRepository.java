package com.tcet.RestApi.repositories;

import com.tcet.RestApi.model.JobOpening;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {

    @EntityGraph(attributePaths = "company")
    @Query("select j from JobOpening j")
    List<JobOpening> findAllDetailed();

    @EntityGraph(attributePaths = "company")
    @Query("select j from JobOpening j where j.company.id = :companyId")
    List<JobOpening> findByCompanyId(@Param("companyId") Long companyId);

    @EntityGraph(attributePaths = "company")
    @Query("select j from JobOpening j where j.id = :id")
    Optional<JobOpening> findDetailedById(@Param("id") Long id);

    boolean existsByCompany_CompanyNameIgnoreCaseAndJobTitleIgnoreCase(String companyName, String jobTitle);
}
