package com.axlero.ragassistant.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** The structured reply requested from the model: the answer and the numbers of the context chunks it used. */
public record LlmAnswer(
        @JsonPropertyDescription("The answer, written only from the numbered context chunks")
        String answer,
        @JsonPropertyDescription("Numbers of the context chunks actually used, for example [1, 3]")
        List<Integer> sources) {
}
