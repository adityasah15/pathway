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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Adds a deterministic demo dataset on top of whatever is already in the database.
 * Every insert is guarded, so running the application twice does not duplicate data.
 *
 * <p>On top of the 24/14/18/80 base dataset this seeds six scripted applications
 * with full multi-step status histories, four edge-case job openings (two expired,
 * one closing today, one new with no applicants), and a few deliberately untidy
 * records for the pandas cleaning path. All application dates are generated inside
 * the job's posted/deadline window.</p>
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
    private static final int TARGET_BASE_APPLICATIONS = 80;
    private static final int SCENARIO_APPLICATIONS = 6;
    private static final int TARGET_APPLICATIONS = TARGET_BASE_APPLICATIONS + SCENARIO_APPLICATIONS;

    private static final String EDGE_COMPANY = "Atlas Manufacturing";

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
            "Mechanical", "Civil", "Artificial Intelligence"
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
    private static final String[] APPLICATION_NOTES = {
            "Campus placement drive",
            "Applied through the placement portal",
            "Referred by the placement cell",
            "On-campus recruitment"
    };

    private static final List<ApplicationStatus> PROGRESSION = List.of(
            ApplicationStatus.APPLIED,
            ApplicationStatus.SHORTLISTED,
            ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFERED,
            ApplicationStatus.ACCEPTED);

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
        seedBaseJobs(random);
        seedEdgeJobs();
        int scenarios = seedScenarioApplications();
        seedFillerApplications(random);

        log.info("Demo dataset ready: {} students, {} companies, {} jobs, {} applications ({} scripted scenarios)",
                students.count(), companies.count(), jobs.count(), applications.count(), scenarios);
    }

    private void seedStudents(Random random) {
        List<Student> existing = students.findAll();
        long needed = Math.max(0, TARGET_STUDENTS - existing.size());
        boolean reuseExistingAiLabel = existing.stream()
                .anyMatch(student -> "AI & Data Science".equalsIgnoreCase(student.getDepartment()));

        for (int i = 0; i < needed; i++) {
            String rollNo = String.format("SEED%03d", 100 + i);
            if (students.existsByRollNo(rollNo)) {
                continue;
            }

            Student student = new Student();
            student.setRollNo(rollNo);
            student.setFullName(FIRST_NAMES[i % FIRST_NAMES.length] + " " + LAST_NAMES[i % LAST_NAMES.length]);

            if (i == 5) {
                student.setEmail(null);
            } else {
                student.setEmail(String.format("demo%03d@placement.local", 100 + i));
            }

            String department = DEPARTMENTS[i % DEPARTMENTS.length];
            if ("Artificial Intelligence".equals(department) && reuseExistingAiLabel) {
                department = "AI & Data Science";
            }
            if (i == 17) {
                // Deliberately untidy: pandas trims/collapses this back to Information Technology.
                department = "  information   technology ";
            }
            student.setDepartment(department);

            double cgpa = round(6.4 + random.nextDouble() * 3.2);
            student.setCgpa(i == 11 ? null : cgpa);
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

    private void seedBaseJobs(Random random) {
        List<Company> available = companies.findAll();
        if (available.isEmpty()) {
            return;
        }

        long needed = Math.max(0, TARGET_JOBS - jobs.count());
        for (int i = 0; i < needed; i++) {
            JobOpening job = new JobOpening();
            job.setCompany(available.get(random.nextInt(available.size())));
            job.setJobTitle(JOB_TITLES[i % JOB_TITLES.length]);
            // Cycle instead of drawing randomly so every job type is represented.
            job.setJobType(JOB_TYPES[i % JOB_TYPES.length]);
            job.setPackageLpa(round(4.0 + random.nextDouble() * 14.0));
            job.setMinCgpa(round(6.5 + random.nextDouble() * 2.0));
            job.setPostedDate(LocalDate.now().minusDays(20L + (long) i * 8 + random.nextInt(8)));
            job.setDeadline(LocalDate.now().plusDays(20L + random.nextInt(150)));
            jobs.save(job);
        }
    }

    /**
     * Four openings that make the failure/empty states visible in the UI:
     * two already expired, one closing today, and one freshly posted opening.
     */
    private void seedEdgeJobs() {
        Company edge = companies.findAll().stream()
                .filter(company -> EDGE_COMPANY.equalsIgnoreCase(company.getCompanyName()))
                .findFirst()
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setCompanyName(EDGE_COMPANY);
                    company.setIndustry("Manufacturing");
                    company.setLocation("Pune");
                    company.setWebsite("https://demo.example.com/" + slug(EDGE_COMPANY));
                    return companies.save(company);
                });

        record EdgeJob(String title, String type, double packageLpa, double minCgpa,
                       int postedDaysAgo, int deadlineDaysFromToday) {
        }

        List<EdgeJob> edgeJobs = List.of(
                new EdgeJob("Backend Engineer", "Full-time", 12.0, 7.0, 90, -30),
                new EdgeJob("Data Analyst", "Full-time", 8.5, 7.0, 90, -10),
                new EdgeJob("QA Engineer", "Full-time", 7.5, 6.5, 30, 0),
                new EdgeJob("Support Engineer", "Internship", 5.0, 6.0, 0, 60)
        );

        for (EdgeJob spec : edgeJobs) {
            if (jobs.existsByCompany_CompanyNameIgnoreCaseAndJobTitleIgnoreCase(EDGE_COMPANY, spec.title())) {
                continue;
            }
            JobOpening job = new JobOpening();
            job.setCompany(edge);
            job.setJobTitle(spec.title());
            job.setJobType(spec.type());
            job.setPackageLpa(spec.packageLpa());
            job.setMinCgpa(spec.minCgpa());
            job.setPostedDate(LocalDate.now().minusDays(spec.postedDaysAgo()));
            job.setDeadline(LocalDate.now().plusDays(spec.deadlineDaysFromToday()));
            jobs.save(job);
        }
    }

    /**
     * Scripted records that guarantee the demo always contains a rejected-after-interview
     * history, a withdrawn-after-offer history, a complete accepted path, and active
     * applications at every stage.
     */
    private int seedScenarioApplications() {
        List<Student> studentList = students.findAll();
        List<JobOpening> allJobs = jobs.findAllDetailed();
        List<JobOpening> baseJobs = allJobs.stream()
                .filter(job -> !isEdgeCompany(job))
                .toList();
        List<JobOpening> edgeJobs = allJobs.stream()
                .filter(this::isEdgeCompany)
                .toList();
        Set<String> usedPairs = usedPairs();

        int created = 0;
        created += seedScenario(
                "Demo scenario: rejected after interview",
                edgeJobs, studentList, usedPairs,
                List.of(
                        stage(ApplicationStatus.APPLIED, 80, "Application received"),
                        stage(ApplicationStatus.SHORTLISTED, 74, "Shortlisted after resume screening"),
                        stage(ApplicationStatus.INTERVIEW, 65, "Interview completed"),
                        stage(ApplicationStatus.REJECTED, 50, "Rejected after interview")),
                LocalDate.now().minusDays(65), 0, true) ? 1 : 0;

        created += seedScenario(
                "Demo scenario: withdrawn after offer",
                baseJobs, studentList, usedPairs,
                List.of(
                        stage(ApplicationStatus.APPLIED, 70, "Application received"),
                        stage(ApplicationStatus.SHORTLISTED, 64, "Shortlisted after resume screening"),
                        stage(ApplicationStatus.INTERVIEW, 55, "Interview completed"),
                        stage(ApplicationStatus.OFFERED, 45, "Offer released"),
                        stage(ApplicationStatus.WITHDRAWN, 40, "Candidate withdrew after offer")),
                LocalDate.now().minusDays(55), 1, false) ? 1 : 0;

        created += seedScenario(
                "Demo scenario: accepted end-to-end",
                baseJobs, studentList, usedPairs,
                List.of(
                        stage(ApplicationStatus.APPLIED, 100, "Application received"),
                        stage(ApplicationStatus.SHORTLISTED, 94, "Shortlisted after resume screening"),
                        stage(ApplicationStatus.INTERVIEW, 85, "Interview completed"),
                        stage(ApplicationStatus.OFFERED, 75, "Offer released"),
                        stage(ApplicationStatus.ACCEPTED, 70, "Offer accepted by candidate")),
                LocalDate.now().minusDays(85), 2, false) ? 1 : 0;

        created += seedScenario(
                "Demo scenario: stuck at applied",
                baseJobs, studentList, usedPairs,
                List.of(stage(ApplicationStatus.APPLIED, 12, "Application received")),
                null, 3, false) ? 1 : 0;

        created += seedScenario(
                "Demo scenario: offered pending decision",
                baseJobs, studentList, usedPairs,
                List.of(
                        stage(ApplicationStatus.APPLIED, 35, "Application received"),
                        stage(ApplicationStatus.SHORTLISTED, 30, "Shortlisted after resume screening"),
                        stage(ApplicationStatus.INTERVIEW, 22, "Interview completed"),
                        stage(ApplicationStatus.OFFERED, 15, "Offer released")),
                LocalDate.now().minusDays(22), 4, false) ? 1 : 0;

        created += seedScenario(
                "Demo scenario: interview scheduled",
                baseJobs, studentList, usedPairs,
                List.of(
                        stage(ApplicationStatus.APPLIED, 20, "Application received"),
                        stage(ApplicationStatus.SHORTLISTED, 15, "Shortlisted after resume screening"),
                        stage(ApplicationStatus.INTERVIEW, 12, "Interview scheduled")),
                LocalDate.now().plusDays(5), 5, false) ? 1 : 0;

        if (created < SCENARIO_APPLICATIONS) {
            log.warn("Demo seeder created {} of {} scripted scenarios", created, SCENARIO_APPLICATIONS);
        }
        return created;
    }

    private boolean seedScenario(String marker, List<JobOpening> jobCandidates,
                                 List<Student> studentList, Set<String> usedPairs,
                                 List<ScenarioStage> stages, LocalDate interviewDate,
                                 int offset, boolean requireExpiredJob) {
        if (applications.existsByNotes(marker)) {
            return true;
        }
        if (jobCandidates.isEmpty() || studentList.isEmpty() || stages.isEmpty()) {
            return false;
        }

        LocalDate appliedDate = stages.get(0).changedAt().toLocalDate();
        boolean hasOffer = stages.stream().anyMatch(stage -> stage.status() == ApplicationStatus.OFFERED);

        for (int i = 0; i < studentList.size(); i++) {
            Student student = studentList.get((offset + i) % studentList.size());
            if (student.getCgpa() == null) {
                continue;
            }
            JobOpening job = pickScenarioJob(jobCandidates, student, appliedDate, usedPairs, offset, requireExpiredJob);
            if (job == null) {
                continue;
            }

            JobApplication application = new JobApplication();
            application.setStudent(student);
            application.setJob(job);
            application.setAppliedDate(appliedDate);
            application.setStatus(stages.get(stages.size() - 1).status().label());
            application.setInterviewDate(interviewDate);
            application.setOfferPackageLpa(hasOffer ? packageFor(job) : null);
            application.setNotes(marker);

            JobApplication saved = applications.save(application);
            for (ScenarioStage stage : stages) {
                history.save(new ApplicationStatusHistory(
                        saved, stage.status().label(), stage.changedAt(), "placement.cell", stage.note()));
            }
            log.info("Seeded scenario '{}' for {} / {}", marker, student.getRollNo(), job.getJobTitle());
            return true;
        }

        log.warn("Could not find an eligible student/job pair for scenario '{}'", marker);
        return false;
    }

    private JobOpening pickScenarioJob(List<JobOpening> candidates, Student student, LocalDate appliedDate,
                                       Set<String> usedPairs, int offset, boolean requireExpiredJob) {
        for (int i = 0; i < candidates.size(); i++) {
            JobOpening job = candidates.get((offset + i) % candidates.size());
            if (requireExpiredJob && (job.getDeadline() == null || !job.getDeadline().isBefore(LocalDate.now()))) {
                continue;
            }
            if (job.getPostedDate() != null && appliedDate.isBefore(job.getPostedDate())) {
                continue;
            }
            if (job.getDeadline() != null && appliedDate.isAfter(job.getDeadline())) {
                continue;
            }
            if (student.getCgpa() != null && job.getMinCgpa() != null
                    && student.getCgpa() < job.getMinCgpa()) {
                continue;
            }
            String pair = pairKey(student, job);
            if (usedPairs.contains(pair)) {
                continue;
            }
            usedPairs.add(pair);
            return job;
        }
        return null;
    }

    private void seedFillerApplications(Random random) {
        long needed = Math.max(0, TARGET_APPLICATIONS - applications.count());
        if (needed == 0) {
            return;
        }

        List<Student> studentList = students.findAll().stream()
                .filter(student -> student.getCgpa() != null)
                .toList();
        List<JobOpening> jobList = jobs.findAllDetailed().stream()
                .filter(job -> !isEdgeCompany(job))
                .filter(job -> job.getDeadline() == null || !job.getDeadline().isBefore(LocalDate.now()))
                .toList();
        if (studentList.isEmpty() || jobList.isEmpty()) {
            log.warn("Skipping filler applications: no eligible students or open jobs");
            return;
        }

        Set<String> usedPairs = usedPairs();
        int created = 0;
        int attempts = 0;
        int maxAttempts = (int) needed * 200;

        while (created < needed && attempts < maxAttempts) {
            attempts++;
            Student student = studentList.get(random.nextInt(studentList.size()));
            JobOpening job = jobList.get(random.nextInt(jobList.size()));

            if (student.getCgpa() != null && job.getMinCgpa() != null
                    && student.getCgpa() < job.getMinCgpa()) {
                continue;
            }
            String pair = pairKey(student, job);
            if (usedPairs.contains(pair)) {
                continue;
            }

            List<ApplicationStatus> path = buildPath(random);
            int steps = path.size() - 1;
            LocalDate today = LocalDate.now();
            LocalDate earliest = job.getPostedDate() != null ? job.getPostedDate() : today.minusDays(180);
            LocalDate latest = today.minusDays(steps + 1L);
            if (job.getDeadline() != null && latest.isAfter(job.getDeadline())) {
                latest = job.getDeadline();
            }

            if (earliest.isAfter(latest)) {
                // Not enough room in the posting window for a multi-stage story.
                path = List.of(ApplicationStatus.APPLIED);
                steps = 0;
                latest = today;
                if (job.getDeadline() != null && latest.isAfter(job.getDeadline())) {
                    latest = job.getDeadline();
                }
                if (earliest.isAfter(latest)) {
                    continue;
                }
            }

            LocalDate appliedDate = randomDateBetween(earliest, latest, random);
            List<HistoryEntry> entries = buildHistory(path, appliedDate, random);
            LocalDate interviewDate = entries.stream()
                    .filter(entry -> entry.status() == ApplicationStatus.INTERVIEW)
                    .findFirst()
                    .map(entry -> entry.changedAt().toLocalDate())
                    .orElse(null);
            boolean hasOffer = path.contains(ApplicationStatus.OFFERED);

            JobApplication application = new JobApplication();
            application.setStudent(student);
            application.setJob(job);
            application.setAppliedDate(appliedDate);
            application.setStatus(path.get(path.size() - 1).label());
            application.setInterviewDate(interviewDate);
            application.setOfferPackageLpa(hasOffer ? packageFor(job) : null);
            application.setNotes(APPLICATION_NOTES[created % APPLICATION_NOTES.length]);

            JobApplication saved = applications.save(application);
            for (HistoryEntry entry : entries) {
                history.save(new ApplicationStatusHistory(
                        saved, entry.status().label(), entry.changedAt(), entry.changedBy(), entry.note()));
            }
            usedPairs.add(pair);
            created++;
        }

        if (created < needed) {
            log.warn("Demo seeder could only add {} of {} requested filler applications", created, needed);
        }
    }

    private List<HistoryEntry> buildHistory(List<ApplicationStatus> path, LocalDate appliedDate,
                                            Random random) {
        List<HistoryEntry> entries = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate cursorDate = appliedDate;
        entries.add(new HistoryEntry(
                ApplicationStatus.APPLIED,
                appliedDate.atTime(9, 30),
                "placement.cell",
                "Application received"));

        for (int i = 1; i < path.size(); i++) {
            int stepsLeft = path.size() - 1 - i;
            long remaining = ChronoUnit.DAYS.between(cursorDate, today);
            int maxGap = (int) Math.max(1, Math.min(10, remaining - stepsLeft));
            cursorDate = cursorDate.plusDays(1 + random.nextInt(maxGap));
            if (cursorDate.isAfter(today)) {
                cursorDate = today;
            }
            entries.add(new HistoryEntry(
                    path.get(i),
                    cursorDate.atTime(Math.min(18, 10 + i), (i * 17) % 60),
                    i % 2 == 0 ? "placement.cell" : "admin",
                    transitionNote(path.get(i), path.get(i - 1))));
        }
        return entries;
    }

    private List<ApplicationStatus> buildPath(Random random) {
        ApplicationStatus finalStatus = weightedStatus(random);
        return switch (finalStatus) {
            case APPLIED -> List.of(ApplicationStatus.APPLIED);
            case SHORTLISTED -> List.of(ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED);
            case INTERVIEW -> List.of(ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED,
                    ApplicationStatus.INTERVIEW);
            case OFFERED -> List.of(ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED,
                    ApplicationStatus.INTERVIEW, ApplicationStatus.OFFERED);
            case ACCEPTED -> PROGRESSION;
            case REJECTED -> withTerminal(progressionTo(closureStage(random)), ApplicationStatus.REJECTED);
            case WITHDRAWN -> withTerminal(progressionTo(closureStage(random)), ApplicationStatus.WITHDRAWN);
        };
    }

    private ApplicationStatus closureStage(Random random) {
        int roll = random.nextInt(100);
        if (roll < 20) {
            return ApplicationStatus.APPLIED;
        }
        if (roll < 50) {
            return ApplicationStatus.SHORTLISTED;
        }
        if (roll < 85) {
            return ApplicationStatus.INTERVIEW;
        }
        return ApplicationStatus.OFFERED;
    }

    private List<ApplicationStatus> progressionTo(ApplicationStatus stage) {
        int index = PROGRESSION.indexOf(stage);
        if (index < 0) {
            return List.of(ApplicationStatus.APPLIED);
        }
        return PROGRESSION.subList(0, index + 1);
    }

    private List<ApplicationStatus> withTerminal(List<ApplicationStatus> path, ApplicationStatus terminal) {
        List<ApplicationStatus> result = new ArrayList<>(path);
        result.add(terminal);
        return result;
    }

    private String transitionNote(ApplicationStatus status, ApplicationStatus previous) {
        return switch (status) {
            case SHORTLISTED -> "Shortlisted after resume screening";
            case INTERVIEW -> "Interview completed";
            case OFFERED -> "Offer released";
            case ACCEPTED -> "Offer accepted by candidate";
            case REJECTED -> switch (previous) {
                case APPLIED -> "Rejected at screening";
                case SHORTLISTED -> "Rejected after shortlisting";
                case INTERVIEW -> "Rejected after interview";
                case OFFERED -> "Rejected after offer";
                default -> "Rejected";
            };
            case WITHDRAWN -> switch (previous) {
                case APPLIED -> "Candidate withdrew before screening";
                case SHORTLISTED -> "Candidate withdrew after shortlisting";
                case INTERVIEW -> "Candidate withdrew after interview";
                case OFFERED -> "Candidate withdrew after offer";
                default -> "Candidate withdrew";
            };
            default -> "Status updated";
        };
    }

    private ApplicationStatus weightedStatus(Random random) {
        int roll = random.nextInt(100);
        if (roll < 28) {
            return ApplicationStatus.APPLIED;
        }
        if (roll < 46) {
            return ApplicationStatus.SHORTLISTED;
        }
        if (roll < 60) {
            return ApplicationStatus.INTERVIEW;
        }
        if (roll < 72) {
            return ApplicationStatus.OFFERED;
        }
        if (roll < 80) {
            return ApplicationStatus.ACCEPTED;
        }
        if (roll < 95) {
            return ApplicationStatus.REJECTED;
        }
        return ApplicationStatus.WITHDRAWN;
    }

    private Set<String> usedPairs() {
        Set<String> pairs = new HashSet<>();
        for (JobApplication application : applications.findAll()) {
            pairs.add(pairKey(application.getStudent(), application.getJob()));
        }
        return pairs;
    }

    private String pairKey(Student student, JobOpening job) {
        return student.getId() + ":" + job.getId();
    }

    private boolean isEdgeCompany(JobOpening job) {
        return job.getCompany() != null && EDGE_COMPANY.equalsIgnoreCase(job.getCompany().getCompanyName());
    }

    private ScenarioStage stage(ApplicationStatus status, int daysAgo, String note) {
        LocalDate date = LocalDate.now().minusDays(daysAgo);
        return new ScenarioStage(status,
                date.atTime(10 + Math.floorMod(daysAgo, 6), Math.floorMod(daysAgo * 7, 60)),
                note);
    }

    private Double packageFor(JobOpening job) {
        double base = job.getPackageLpa() == null ? 8.0 : job.getPackageLpa();
        return round(Math.max(2.0, base + 0.5));
    }

    private LocalDate randomDateBetween(LocalDate start, LocalDate end, Random random) {
        long span = ChronoUnit.DAYS.between(start, end);
        if (span <= 0) {
            return start;
        }
        return start.plusDays(random.nextInt((int) span + 1));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private record ScenarioStage(ApplicationStatus status, LocalDateTime changedAt, String note) {
    }

    private record HistoryEntry(ApplicationStatus status, LocalDateTime changedAt,
                                String changedBy, String note) {
    }
}
