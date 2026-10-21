package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.Citation;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.RagStreamEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class RagServiceImpl implements RagService {

    static final String NO_ANSWER = "I don't have that information in the uploaded documents.";
    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);
    private static final int SNIPPET_LENGTH = 200;

    private final RetrievalService retrievalService;
    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public RagServiceImpl(RetrievalService retrievalService,
                          ChatClient chatClient,
                          @Value("classpath:prompts/rag-prompt.st") Resource promptResource) {
        this.retrievalService = retrievalService;
        this.chatClient = chatClient;
        this.promptTemplate = PromptTemplate.builder().resource(promptResource).build();
    }

    @Override
    public RagAnswer answer(String question) {
        List<Document> chunks = retrievalService.retrieve(question);
        if (chunks.isEmpty()) {
            // Nothing relevant found: skip the LLM call entirely (cheaper, and it cannot hallucinate).
            return new RagAnswer(NO_ANSWER, List.of());
        }

        String context = chunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        String prompt = promptTemplate.render(Map.of("context", context, "question", question));

        String reply = chatClient.prompt().user(prompt).call().content();
        if (reply == null || reply.isBlank() || reply.contains(NO_ANSWER)){
            return new RagAnswer(NO_ANSWER, List.of());
        }
        return new RagAnswer(reply, toCitations(chunks));
    }

    private List<Citation> toCitations(List<Document> chunks) {
        return chunks.stream().map(this::toCitation).toList();
    }

    private Citation toCitation(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String name = String.valueOf(meta.getOrDefault(ChunkMetadata.FILENAME, "unknown"));
        Object rawPage = meta.getOrDefault(ChunkMetadata.PAGE, meta.get("page_number"));
        Integer page = rawPage instanceof Number n ? n.intValue() : null;
        String text = doc.getText() == null ? "" : doc.getText();
        String snippet = text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "...";
        return new Citation(name, page, snippet);
    }

    @Override
    public Flux<RagStreamEvent> answerStream(String question) {
        return Flux.defer(() -> {
            List<Document> chunks = retrievalService.retrieve(question);
            if (chunks.isEmpty()) {
                // Nothing relevant found: skip the model entirely and stream the fallback sentence.
                return Flux.just(RagStreamEvent.token(NO_ANSWER),
                        RagStreamEvent.citations(List.of()),
                        RagStreamEvent.done());
            }

            String context = chunks.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));
            String prompt = promptTemplate.render(Map.of("context", context, "question", question));

            StringBuilder answer = new StringBuilder();
            Flux<RagStreamEvent> tokens = chatClient.prompt().user(prompt).stream().content()
                    .doOnNext(answer::append)
                    .map(RagStreamEvent::token);
            // Evaluated only after the tokens finish, so the full answer is known by then.
            Flux<RagStreamEvent> tail = Flux.defer(() -> Flux.just(
                    RagStreamEvent.citations(hasAnswer(answer) ? toCitations(chunks) : List.of()),
                    RagStreamEvent.done()));
            return tokens.concatWith(tail);
        }).onErrorResume(e -> {
            log.error("Streaming answer failed", e);
            return Flux.just(RagStreamEvent.error("The answer could not be generated."));
        });
    }

    private static boolean hasAnswer(CharSequence answer) {
        String text = answer.toString();
        return !text.isBlank() && !text.contains(NO_ANSWER);
    }
}
