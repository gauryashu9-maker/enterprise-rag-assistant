package com.axlero.ragassistant.dto;

import java.util.List;
import java.util.Map;

/** One server-sent event of a streamed answer. The data is always a JSON-friendly object. */
public record RagStreamEvent(String event, Object data) {

    public static final String TOKEN = "token";
    public static final String CITATIONS = "citations";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    public static RagStreamEvent token(String text) {
        return new RagStreamEvent(TOKEN, Map.of("text", text == null ? "" : text));
    }

    public static RagStreamEvent citations(List<Citation> citations) {
        return new RagStreamEvent(CITATIONS, Map.of("citations", citations));
    }

    public static RagStreamEvent done() {
        return new RagStreamEvent(DONE, Map.of());
    }

    public static RagStreamEvent error(String detail) {
        return new RagStreamEvent(ERROR, Map.of("detail", detail));
    }
}
