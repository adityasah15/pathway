package com.tcet.RestApi.repositories;
import com.tcet.RestApi.model.JobOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> { List<JobOpening> findByCompanyId(Long companyId); }
