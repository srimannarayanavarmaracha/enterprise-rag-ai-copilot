package com.sriman.rag.config;

import com.sriman.rag.domain.UserAccount;
import com.sriman.rag.rag.DocumentIngestionService;
import com.sriman.rag.repository.KnowledgeDocumentRepository;
import com.sriman.rag.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedRagData(
        UserAccountRepository userRepository,
        KnowledgeDocumentRepository docRepository,
        DocumentIngestionService ingestionService,
        PasswordEncoder encoder
    ) {
        return args -> {
            if (!userRepository.existsByEmail("demo@sriman.dev")) {
                UserAccount demo = new UserAccount();
                demo.setEmail("demo@sriman.dev");
                demo.setFullName("Demo Engineer");
                demo.setPasswordHash(encoder.encode("Demo@1234"));
                userRepository.save(demo);
            }

            if (docRepository.count() == 0) {
                ingestionService.ingestRawText(
                    "microservices-architecture-runbook.md",
                    """
                    Our production Java platform runs on Java 21 with Spring Boot and PostgreSQL pgvector.
                    For distributed transactions across order and inventory services, we use the Transactional Outbox
                    pattern paired with Apache Kafka and idempotency keys to guarantee at-least-once delivery without
                    duplicate charges. Rate limiting at the API gateway uses Redis Lua Token Bucket scripts with
                    an automatic in-memory fallback when Redis experiences transient network partitions.
                    """,
                    "demo@sriman.dev"
                );
            }
        };
    }
}
