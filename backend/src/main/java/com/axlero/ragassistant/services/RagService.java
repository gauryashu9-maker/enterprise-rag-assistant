package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.RagAnswer;

public interface RagService {

    RagAnswer answer(String question);
}