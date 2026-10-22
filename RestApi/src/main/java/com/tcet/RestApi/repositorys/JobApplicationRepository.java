package com.tcet.RestApi.repositorys;
import com.tcet.RestApi.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByStudentId(Long studentId);
    long countByStatusIgnoreCase(String status);
    boolean existsByStudentIdAndJobId(Long studentId, Long jobId);
}
