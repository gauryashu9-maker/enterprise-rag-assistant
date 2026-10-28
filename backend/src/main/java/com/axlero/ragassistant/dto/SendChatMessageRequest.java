package com.axlero.ragassistant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record SendChatMessageRequest(
        @NotBlank(message = "must not be blank")
        @JsonAlias({"content", "question"})
        String message) {
}
