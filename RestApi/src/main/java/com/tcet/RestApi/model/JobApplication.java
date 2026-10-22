package com.tcet.RestApi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(name = "uk_student_job", columnNames = {"student_id", "job_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private JobOpening job;

    @NotNull
    @Column(name = "applied_date", nullable = false)
    private LocalDate appliedDate;

    @NotBlank
    @Column(nullable = false, length = 30)
    private String status = "Applied";

    private LocalDate interviewDate;

    private Double offerPackageLpa;

    @Column(length = 500)
    private String notes;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
