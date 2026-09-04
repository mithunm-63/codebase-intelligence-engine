package com.codeintel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void healthReturnsUp() {
        Map<String, Object> response = new HealthController().health();
        assertThat(response).containsEntry("status", "UP");
        assertThat(response).containsEntry("service", "codebase-intelligence-backend");
        assertThat(response).containsKey("timestamp");
    }
}
