package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.RagStreamEvent;
import reactor.core.publisher.Flux;

public interface RagService {

    RagAnswer answer(String question);

    /** Streams the answer: token events, then one citations event, then done (or one error event). */
    Flux<RagStreamEvent> answerStream(String question);
}
