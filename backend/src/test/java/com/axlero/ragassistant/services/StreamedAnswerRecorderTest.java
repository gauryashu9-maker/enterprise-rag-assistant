package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axlero.ragassistant.dto.RagStreamEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class StreamedAnswerRecorderTest {

    private final List<String> saved = new CopyOnWriteArrayList<>();

    private List<RagStreamEvent> run(Flux<RagStreamEvent> events) {
        return StreamedAnswerRecorder.recordWhenDone(events, saved::add).collectList().block();
    }

    @Test
    void savesTheCompleteAnswerOnceAfterDoneAndPassesEventsThroughUnchanged() {
        List<RagStreamEvent> out = run(Flux.just(
                RagStreamEvent.token("24"), RagStreamEvent.token(" days"),
                RagStreamEvent.citations(List.of()), RagStreamEvent.done()));

        assertEquals(List.of("token", "token", "citations", "done"),
                out.stream().map(RagStreamEvent::event).toList());
        assertEquals(List.of("24 days"), saved);
    }

    @Test
    void savesNothingWhenTheServerReportsAnError() {
        run(Flux.just(RagStreamEvent.token("par"), RagStreamEvent.error("failed")));

        assertTrue(saved.isEmpty());
    }

    @Test
    void savesNothingWhenTheStreamEndsWithoutDone() {
        run(Flux.just(RagStreamEvent.token("par")));

        assertTrue(saved.isEmpty());
    }

    @Test
    void savesNothingWhenTheClientCancelsMidStream() {
        List<RagStreamEvent> out = StreamedAnswerRecorder
                .recordWhenDone(Flux.concat(Flux.just(RagStreamEvent.token("x")), Flux.never()), saved::add)
                .take(1)
                .collectList()
                .block();

        assertEquals(1, out.size());
        assertTrue(saved.isEmpty());
    }

    @Test
    void savesNothingForAnEmptyAnswer() {
        run(Flux.just(RagStreamEvent.citations(List.of()), RagStreamEvent.done()));

        assertTrue(saved.isEmpty());
    }

    @Test
    void aFailingSaveDoesNotBreakTheStream() {
        List<RagStreamEvent> out = StreamedAnswerRecorder
                .recordWhenDone(Flux.just(RagStreamEvent.token("ok"), RagStreamEvent.done()),
                        answer -> {
                            throw new IllegalStateException("database down");
                        })
                .collectList()
                .block();

        assertEquals(List.of("token", "done"), out.stream().map(RagStreamEvent::event).toList());
    }
}
