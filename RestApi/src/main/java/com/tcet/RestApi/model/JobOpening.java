package com.tcet.RestApi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Table(name="job_openings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class JobOpening {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @NotNull @Column(name="company_id", nullable=false) private Long companyId;
    @NotBlank @Column(name="job_title", nullable=false, length=140) private String jobTitle;
    @NotBlank @Column(name="job_type", nullable=false, length=40) private String jobType;
    @DecimalMin("0.0") private Double packageLpa;
    @DecimalMin("0.0") @DecimalMax("10.0") private Double minCgpa;
    private LocalDate postedDate;
    private LocalDate deadline;
}
