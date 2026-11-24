package com.sriman.rag.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class RagCopilotIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void shouldRejectUnauthenticatedDocumentRequests() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldRegisterIngestDocumentAndAnswerWithCitations() throws Exception {
        String registerPayload = """
            {
              "email": "architect@sriman.dev",
              "password": "Password@123",
              "fullName": "Lead Architect"
            }
            """;

        MvcResult authRes = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();

        JsonNode authJson = objectMapper.readTree(authRes.getResponse().getContentAsString());
        String token = authJson.get("token").asText();

        String docPayload = """
            {
              "title": "pgvector-indexing-guide.md",
              "content": "HNSW indexes in PostgreSQL pgvector accelerate high-dimensional cosine similarity search while maintaining high recall under low latency SLAs."
            }
            """;

        mockMvc.perform(post("/api/v1/documents/text")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(docPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.chunkCount").value(1));

        String askPayload = """
            {
              "question": "How does pgvector accelerate cosine similarity search?",
              "topK": 3
            }
            """;

        mockMvc.perform(post("/api/v1/chat/ask")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(askPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.citations.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("HNSW")));
    }
}
