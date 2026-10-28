package com.axlero.ragassistant.dto;

import java.util.List;

public record SendChatMessageResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        List<Citation> citations) {
}
