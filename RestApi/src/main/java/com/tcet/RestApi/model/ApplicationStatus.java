package com.tcet.RestApi.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public enum ApplicationStatus {
    APPLIED("Applied"),
    SHORTLISTED("Shortlisted"),
    INTERVIEW("Interview"),
    OFFERED("Offered"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.of(
            APPLIED, Set.of(SHORTLISTED, REJECTED, WITHDRAWN),
            SHORTLISTED, Set.of(INTERVIEW, REJECTED, WITHDRAWN),
            INTERVIEW, Set.of(OFFERED, REJECTED, WITHDRAWN),
            OFFERED, Set.of(ACCEPTED, REJECTED, WITHDRAWN),
            ACCEPTED, Set.of(),
            REJECTED, Set.of(),
            WITHDRAWN, Set.of());

    public Set<ApplicationStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }

    public boolean canTransitionTo(ApplicationStatus next) {
        return this == next || allowedNext().contains(next);
    }

    public static ApplicationStatus fromLabel(String label) {
        String normalized = label == null ? "" : label.trim();
        return Arrays.stream(values())
                .filter(status -> status.label.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid status. Use: " + labels()));
    }

    public static Set<String> labels() {
        return Arrays.stream(values()).map(ApplicationStatus::label).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
