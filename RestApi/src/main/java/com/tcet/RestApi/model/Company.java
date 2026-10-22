package com.tcet.RestApi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity @Table(name="companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Company {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @NotBlank @Column(name="company_name", nullable=false, unique=true, length=140) private String companyName;
    @Column(length=100) private String industry;
    @Column(length=120) private String location;
    @Column(length=220) private String website;
}
