package com.tcet.RestApi.web;

import java.time.Instant;
import java.util.Map;

/**
 * Single error shape for every API failure so the SPA can always show a useful message.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
