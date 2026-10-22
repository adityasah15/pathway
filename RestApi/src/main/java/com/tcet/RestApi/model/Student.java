package com.tcet.RestApi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity @Table(name="students")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Student {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @NotBlank @Column(name="roll_no", nullable=false, unique=true, length=30) private String rollNo;
    @NotBlank @Column(name="full_name", nullable=false, length=120) private String fullName;
    @Email @Column(length=150) private String email;
    @NotBlank @Column(length=80) private String department;
    @DecimalMin("0.0") @DecimalMax("10.0") private Double cgpa;
    private Integer graduationYear;
}
