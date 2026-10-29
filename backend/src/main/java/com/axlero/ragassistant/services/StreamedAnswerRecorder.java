package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.RagStreamEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Passes a streamed answer through unchanged and, only when it ends with a done event, hands the complete
 * answer text to a callback (used to save the question and answer pair). Nothing is saved when the stream
 * fails, reports an error event, ends without done, or is cancelled by the client.
 */
public final class StreamedAnswerRecorder {

    private static final Logger log = LoggerFactory.getLogger(StreamedAnswerRecorder.class);

    private StreamedAnswerRecorder() {
    }

    public static Flux<RagStreamEvent> recordWhenDone(Flux<RagStreamEvent> events, Consumer<String> onAnswerComplete) {
        return Flux.defer(() -> {
            StringBuilder answer = new StringBuilder();
            AtomicBoolean sawDone = new AtomicBoolean(false);
            AtomicBoolean sawError = new AtomicBoolean(false);

            Flux<RagStreamEvent> observed = events.doOnNext(event -> {
                switch (event.event()) {
                    case RagStreamEvent.TOKEN -> answer.append(tokenText(event));
                    case RagStreamEvent.DONE -> sawDone.set(true);
                    case RagStreamEvent.ERROR -> sawError.set(true);
                    default -> { }
                }
            });

            // Subscribed only after the source completed normally, so cancel and error never reach it.
            Flux<RagStreamEvent> save = Flux.defer(() -> {
                String text = answer.toString();
                if (!sawDone.get() || sawError.get() || text.isBlank()) {
                    return Flux.<RagStreamEvent>empty();
                }
                return Mono.fromRunnable(() -> onAnswerComplete.accept(text))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            // The user already received the answer: log the failure (never the text) and carry on.
                            log.error("Could not save the streamed answer", e);
                            return Mono.empty();
                        })
                        .then(Mono.<RagStreamEvent>empty())
                        .flux();
            });

            return observed.concatWith(save);
        });
    }

    private static String tokenText(RagStreamEvent event) {
        if (event.data() instanceof Map<?, ?> data && data.get("text") instanceof String text) {
            return text;
        }
        return "";
    }
}
