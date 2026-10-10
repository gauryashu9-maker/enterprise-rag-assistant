package com.axlero.ragassistant.dto;

import java.util.List;

public record RagAnswer(String answer, List<Citation> citations) {
}