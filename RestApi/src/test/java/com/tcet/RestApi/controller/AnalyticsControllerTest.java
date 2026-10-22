package com.tcet.RestApi.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
class AnalyticsControllerTest {

    private static HttpServer analyticsServer;
    private static volatile String lastQuery;

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void analyticsServiceUrl(DynamicPropertyRegistry registry) throws IOException {
        if (analyticsServer == null) {
            analyticsServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            analyticsServer.createContext("/dashboard", exchange -> {
                lastQuery = exchange.getRequestURI().getQuery();
                byte[] body = "{\"summary\":{\"applications\":1}}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            analyticsServer.createContext("/export.csv", exchange -> {
                byte[] body = "application_id,status\n1,Offered\n".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/csv");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            analyticsServer.start();
        }
        registry.add("analytics.service.url", () -> "http://127.0.0.1:" + analyticsServer.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        if (analyticsServer != null) {
            analyticsServer.stop(0);
        }
    }

    @BeforeEach
    void reset() {
        lastQuery = null;
    }

    @Test
    void forwardsDashboardFilters() throws Exception {
        mvc.perform(get("/api/analytics/dashboard")
                        .param("department", "Information Technology")
                        .param("graduationYear", "2027")
                        .param("status", "Offered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.applications").value(1));

        assertThat(lastQuery)
                .contains("department=Information")
                .contains("graduationYear=2027")
                .contains("status=Offered");
    }

    @Test
    void omitsBlankFilters() throws Exception {
        mvc.perform(get("/api/analytics/dashboard").param("department", " "))
                .andExpect(status().isOk());

        assertThat(lastQuery).isNull();
    }

    @Test
    void proxiesCsvExport() throws Exception {
        mvc.perform(get("/api/analytics/export.csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=applications.csv"))
                .andExpect(content().string(containsString("application_id")));
    }
}
