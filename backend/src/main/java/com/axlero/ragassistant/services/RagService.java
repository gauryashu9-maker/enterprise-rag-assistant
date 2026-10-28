package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.RagStreamEvent;
import java.util.UUID;
import reactor.core.publisher.Flux;

public interface RagService {

    /** Answers from the given user's documents only. */
    RagAnswer answer(String question, UUID ownerId);

    /** Streams the answer: token events, then one citations event, then done (or one error event). */
    Flux<RagStreamEvent> answerStream(String question, UUID ownerId);
}
