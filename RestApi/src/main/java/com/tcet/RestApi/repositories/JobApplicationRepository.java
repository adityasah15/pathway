package com.tcet.RestApi.repositories;

import com.tcet.RestApi.model.JobApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @EntityGraph(attributePaths = {"student", "job", "job.company"})
    @Query("select a from JobApplication a")
    List<JobApplication> findAllDetailed();

    @EntityGraph(attributePaths = {"student", "job", "job.company"})
    @Query("select a from JobApplication a where a.student.id = :studentId")
    List<JobApplication> findByStudentId(@Param("studentId") Long studentId);

    @EntityGraph(attributePaths = {"student", "job", "job.company"})
    @Query("select a from JobApplication a where a.id = :id")
    Optional<JobApplication> findDetailedById(@Param("id") Long id);

    long countByStatusIgnoreCase(String status);

    boolean existsByStudent_IdAndJob_Id(Long studentId, Long jobId);

    boolean existsByStudent_IdAndJob_IdAndIdNot(Long studentId, Long jobId, Long id);

    boolean existsByNotes(String notes);
}
