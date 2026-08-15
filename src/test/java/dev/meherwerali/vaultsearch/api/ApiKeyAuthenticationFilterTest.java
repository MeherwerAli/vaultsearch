package dev.meherwerali.vaultsearch.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meherwerali.vaultsearch.config.ApiKeyAuthenticationFilter;
import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiKeyAuthenticationFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VaultSearchProperties properties = new VaultSearchProperties();
        properties.setApiKey("test-key");
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(properties, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController()).addFilters(filter).build();
    }

    @Test
    void rejectsMissingOrIncorrectBearerKeysWithProblemJson() throws Exception {
        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/probe").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsCorrectBearerKeyAndLeavesHealthUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/probe").header("Authorization", "Bearer test-key"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/probe")
        String probe() {
            return "ok";
        }
    }
}
