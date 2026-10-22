package com.tcet.RestApi.config;

import com.tcet.RestApi.model.ApplicationStatus;
import com.tcet.RestApi.model.ApplicationStatusHistory;
import com.tcet.RestApi.model.Company;
import com.tcet.RestApi.model.JobApplication;
import com.tcet.RestApi.model.JobOpening;
import com.tcet.RestApi.model.Student;
import com.tcet.RestApi.repositories.ApplicationStatusHistoryRepository;
import com.tcet.RestApi.repositories.CompanyRepository;
import com.tcet.RestApi.repositories.JobApplicationRepository;
import com.tcet.RestApi.repositories.JobOpeningRepository;
import com.tcet.RestApi.repositories.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Adds a deterministic demo dataset on top of whatever is already in the database.
 * Every insert is guarded, so running the application twice does not duplicate data.
 *
 * Enable with SPRING_PROFILES_ACTIVE=demo (disable with app.demo.seed=false).
 */
@Component
@Profile("demo")
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    private static final int TARGET_STUDENTS = 24;
    private static final int TARGET_COMPANIES = 14;
    private static final int TARGET_JOBS = 18;
    private static final int TARGET_APPLICATIONS = 80;

    private static final String[] FIRST_NAMES = {
            "Isha", "Rohan", "Ananya", "Kabir", "Meera", "Arjun", "Sanya",
            "Dev", "Naina", "Vihaan", "Tara", "Aditya", "Priya"
    };
    private static final String[] LAST_NAMES = {
            "Menon", "Kapoor", "Iyer", "Sharma", "Nair", "Verma", "Bose",
            "Patel", "Reddy", "Khan", "Gupta", "Joshi", "Rao"
    };
    private static final String[] DEPARTMENTS = {
            "Information Technology", "Computer Engineering", "Electronics",
            "Mechanical", "Civil", "AI & Data Science"
    };
    private static final String[] COMPANY_NAMES = {
            "Nimbus Systems", "Vertex Labs", "Orion Digital", "Cobalt Software",
            "Lumen Analytics", "Aster Technologies", "Quanta Consulting", "Meridian Bank",
            "Zenith Retail", "Pinnacle Foods", "Orbit Media", "Cascade Energy",
            "Summit Logistics", "Beacon Health", "Atlas Manufacturing"
    };
    private static final String[] INDUSTRIES = {
            "Software", "Analytics", "Fintech", "Consulting", "E-commerce", "Healthcare"
    };
    private static final String[] LOCATIONS = {
            "Bengaluru", "Pune", "Hyderabad", "Mumbai", "Chennai", "Remote"
    };
    private static final String[] JOB_TITLES = {
            "Software Engineer", "Data Analyst", "Java Developer", "Frontend Developer",
            "Backend Engineer", "QA Engineer", "DevOps Engineer", "Business Analyst",
            "ML Engineer", "Support Engineer", "Product Analyst", "Cloud Engineer"
    };
    private static final String[] JOB_TYPES = {"Full-time", "Internship", "Part-time", "Contract"};

    private final StudentRepository students;
    private final CompanyRepository companies;
    private final JobOpeningRepository jobs;
    private final JobApplicationRepository applications;
    private final ApplicationStatusHistoryRepository history;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Random random = new Random(20261022L);
        seedStudents(random);
        seedCompanies(random);
        seedJobs(random);
        seedApplications(random);

        log.info("Demo dataset ready: {} students, {} companies, {} jobs, {} applications",
                students.count(), companies.count(), jobs.count(), applications.count());
    }

    private void seedStudents(Random random) {
        long needed = Math.max(0, TARGET_STUDENTS - students.count());
        for (int i = 0; i < needed; i++) {
            String rollNo = String.format("SEED%03d", 100 + i);
            if (students.existsByRollNo(rollNo)) {
                continue;
            }
            Student student = new Student();
            student.setRollNo(rollNo);
            student.setFullName(FIRST_NAMES[i % FIRST_NAMES.length] + " " + LAST_NAMES[i % LAST_NAMES.length]);
            student.setEmail(String.format("demo%03d@placement.local", 100 + i));
            student.setDepartment(DEPARTMENTS[i % DEPARTMENTS.length]);
            student.setCgpa(round(6.4 + random.nextDouble() * 3.2));
            student.setGraduationYear(2026 + random.nextInt(3));
            students.save(student);
        }
    }

    private void seedCompanies(Random random) {
        long needed = Math.max(0, TARGET_COMPANIES - companies.count());
        for (int i = 0; i < needed; i++) {
            String name = COMPANY_NAMES[i % COMPANY_NAMES.length];
            if (i >= COMPANY_NAMES.length) {
                name = name + " " + (i / COMPANY_NAMES.length + 1);
            }
            if (companies.existsByCompanyNameIgnoreCase(name)) {
                continue;
            }
            Company company = new Company();
            company.setCompanyName(name);
            company.setIndustry(INDUSTRIES[random.nextInt(INDUSTRIES.length)]);
            company.setLocation(LOCATIONS[random.nextInt(LOCATIONS.length)]);
            company.setWebsite("https://demo.example.com/" + slug(name));
            companies.save(company);
        }
    }

    private void seedJobs(Random random) {
        List<Company> available = companies.findAll();
        if (available.isEmpty()) {
            return;
        }

        long needed = Math.max(0, TARGET_JOBS - jobs.count());
        for (int i = 0; i < needed; i++) {
            JobOpening job = new JobOpening();
            job.setCompany(available.get(random.nextInt(available.size())));
            job.setJobTitle(JOB_TITLES[i % JOB_TITLES.length]);
            job.setJobType(JOB_TYPES[random.nextInt(JOB_TYPES.length)]);
            job.setPackageLpa(round(4.0 + random.nextDouble() * 14.0));
            job.setMinCgpa(round(6.5 + random.nextDouble() * 2.0));
            job.setPostedDate(LocalDate.now().minusDays(10 + random.nextInt(120)));
            job.setDeadline(LocalDate.now().plusDays(20 + random.nextInt(180)));
            jobs.save(job);
        }
    }

    private void seedApplications(Random random) {
        long needed = Math.max(0, TARGET_APPLICATIONS - applications.count());
        if (needed == 0) {
            return;
        }

        List<Student> studentList = students.findAll();
        List<JobOpening> jobList = jobs.findAll();
        if (studentList.isEmpty() || jobList.isEmpty()) {
            return;
        }

        Set<String> usedPairs = new HashSet<>();
        for (JobApplication existing : applications.findAll()) {
            usedPairs.add(existing.getStudent().getId() + ":" + existing.getJob().getId());
        }

        List<Student> eligibleStudents = new ArrayList<>(studentList);
        int created = 0;
        int attempts = 0;
        int maxAttempts = (int) needed * 100;

        while (created < needed && attempts < maxAttempts) {
            attempts++;
            Student student = eligibleStudents.get(random.nextInt(eligibleStudents.size()));
            JobOpening job = jobList.get(random.nextInt(jobList.size()));

            if (student.getCgpa() != null && job.getMinCgpa() != null
                    && student.getCgpa() < job.getMinCgpa()) {
                continue;
            }
            if (!usedPairs.add(student.getId() + ":" + job.getId())) {
                continue;
            }

            LocalDate appliedDate = LocalDate.now().minusDays(1 + random.nextInt(240));
            if (job.getDeadline() != null && appliedDate.isAfter(job.getDeadline())) {
                continue;
            }

            ApplicationStatus status = weightedStatus(random);
            JobApplication application = new JobApplication();
            application.setStudent(student);
            application.setJob(job);
            application.setAppliedDate(appliedDate);
            application.setStatus(status.label());
            application.setNotes("Seeded demo application");

            if (interviewStage(status)) {
                application.setInterviewDate(appliedDate.plusDays(5 + random.nextInt(30)));
            }
            if (offerStage(status)) {
                double base = job.getPackageLpa() == null ? 6.0 : job.getPackageLpa();
                application.setOfferPackageLpa(round(Math.max(2.0, base + (random.nextDouble() - 0.4) * 2.0)));
            }

            JobApplication saved = applications.save(application);
            history.save(new ApplicationStatusHistory(
                    saved, status.label(), LocalDateTime.now(), "demo-seeder", "Seeded demo data"));
            created++;
        }

        if (created < needed) {
            log.warn("Demo seeder could only add {} of {} requested applications", created, needed);
        }
    }

    private ApplicationStatus weightedStatus(Random random) {
        int roll = random.nextInt(100);
        if (roll < 25) {
            return ApplicationStatus.APPLIED;
        }
        if (roll < 42) {
            return ApplicationStatus.SHORTLISTED;
        }
        if (roll < 57) {
            return ApplicationStatus.INTERVIEW;
        }
        if (roll < 72) {
            return ApplicationStatus.OFFERED;
        }
        if (roll < 84) {
            return ApplicationStatus.ACCEPTED;
        }
        if (roll < 95) {
            return ApplicationStatus.REJECTED;
        }
        return ApplicationStatus.WITHDRAWN;
    }

    private boolean interviewStage(ApplicationStatus status) {
        return status == ApplicationStatus.SHORTLISTED
                || status == ApplicationStatus.INTERVIEW
                || status == ApplicationStatus.OFFERED
                || status == ApplicationStatus.ACCEPTED;
    }

    private boolean offerStage(ApplicationStatus status) {
        return status == ApplicationStatus.OFFERED || status == ApplicationStatus.ACCEPTED;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String slug(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
