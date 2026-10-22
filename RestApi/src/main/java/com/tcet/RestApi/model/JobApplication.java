package com.tcet.RestApi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name="applications", uniqueConstraints=@UniqueConstraint(name="uk_student_job", columnNames={"student_id","job_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class JobApplication {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @NotNull @Column(name="student_id", nullable=false) private Long studentId;
    @NotNull @Column(name="job_id", nullable=false) private Long jobId;
    @NotNull @Column(name="applied_date", nullable=false) private LocalDate appliedDate;
    @NotBlank @Column(nullable=false, length=30) private String status = "Applied";
    private LocalDate interviewDate;
    private Double offerPackageLpa;
    @Column(length=500) private String notes;
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    public void updateTimestamp() { updatedAt = LocalDateTime.now(); }
}
