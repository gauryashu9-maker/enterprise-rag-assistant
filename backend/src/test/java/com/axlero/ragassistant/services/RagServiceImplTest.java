package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.dto.RagAnswer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

class RagServiceImplTest {

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final RagServiceImpl service =
            new RagServiceImpl(retrieval, chatClient, new ClassPathResource("prompts/rag-prompt.st"));

    @Test
    void returnsFallbackAndSkipsLlmWhenNothingRetrieved() {
        when(retrieval.retrieve("unknown")).thenReturn(List.of());

        RagAnswer answer = service.answer("unknown");

        assertEquals(RagServiceImpl.NO_ANSWER, answer.answer());
        assertEquals(0, answer.citations().size());
        verifyNoInteractions(chatClient);
    }

    @SuppressWarnings("null")
	@Test
    void answersFromRetrievedChunksWithCitations() {
        @SuppressWarnings("null")
		Document chunk = new Document("Employees get 24 days of leave.", Map.of("filename", "hr-policy.pdf", "page", 2));
        when(retrieval.retrieve("leave?")).thenReturn(List.of(chunk));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("24 days.");

        RagAnswer answer = service.answer("leave?");

        assertEquals("24 days.", answer.answer());
        assertEquals(1, answer.citations().size());
        assertEquals("hr-policy.pdf", answer.citations().get(0).documentName());
        assertEquals(2, answer.citations().get(0).page());
    }
    @SuppressWarnings("null")
	@Test
    void dropsCitationsWhenModelSaysItDoesNotKnow() {
        @SuppressWarnings("null")
		Document chunk = new Document("Unrelated text.", Map.of("filename", "a.pdf", "page", 1));
        when(retrieval.retrieve("q")).thenReturn(List.of(chunk));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(RagServiceImpl.NO_ANSWER);

        RagAnswer answer = service.answer("q");

        assertEquals(RagServiceImpl.NO_ANSWER, answer.answer());
        assertEquals(0, answer.citations().size());
    }

    @SuppressWarnings("null")
	@Test
    void returnsFallbackWhenModelReturnsNull() {
        @SuppressWarnings("null")
		Document chunk = new Document("Some text.", Map.of("filename", "a.pdf", "page", 1));
        when(retrieval.retrieve("q")).thenReturn(List.of(chunk));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(null);

        RagAnswer answer = service.answer("q");

        assertEquals(RagServiceImpl.NO_ANSWER, answer.answer());
        assertEquals(0, answer.citations().size());
    }
}