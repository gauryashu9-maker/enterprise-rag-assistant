package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axlero.ragassistant.dto.LlmAnswer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;

class StructuredAnswerContractTest {

    private final BeanOutputConverter<LlmAnswer> converter = new BeanOutputConverter<>(LlmAnswer.class);

    @Test
    void converterMapsJsonStraightIntoTheRecord() {
        LlmAnswer parsed = converter.convert("{\"answer\":\"24 days\",\"sources\":[1,3]}");

        assertEquals("24 days", parsed.answer());
        assertEquals(List.of(1, 3), parsed.sources());
    }

    @Test
    void converterRejectsReplyThatIsNotJson() {
        assertThrows(RuntimeException.class, () -> converter.convert("Sure! The answer is 24 days."));
    }

    @Test
    void formatInstructionsDescribeTheRecordFields() {
        String format = converter.getFormat();

        assertTrue(format.contains("answer"));
        assertTrue(format.contains("sources"));
    }

    @Test
    void structuredPromptFillsAllPlaceholders() {
        PromptTemplate template = PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/rag-structured-prompt.st"))
                .build();

        String rendered = template.render(Map.of(
                "context", "[1] Leave is 24 days.",
                "question", "How much leave?",
                "format", converter.getFormat()));

        assertTrue(rendered.contains("[1] Leave is 24 days."));
        assertTrue(rendered.contains("How much leave?"));
        assertFalse(rendered.contains("{context}"));
        assertFalse(rendered.contains("{question}"));
        assertFalse(rendered.contains("{format}"));
    }
}
