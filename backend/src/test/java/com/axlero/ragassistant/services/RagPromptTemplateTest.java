package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

class RagPromptTemplateTest {

    @Test
    void rendersContextAndQuestion() {
        PromptTemplate template = PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/rag-prompt.st"))
                .build();

        String rendered = template.render(Map.of("context", "Leave is 24 days.", "question", "How much leave?"));

        assertTrue(rendered.contains("Leave is 24 days."));
        assertTrue(rendered.contains("How much leave?"));
        assertFalse(rendered.contains("{context}"));
    }
}