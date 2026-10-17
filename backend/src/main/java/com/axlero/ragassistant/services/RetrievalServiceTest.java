package com.axlero.ragassistant.services;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

final class RetrievalServiceTestSupport {
    private RetrievalServiceTestSupport() {
    }

    static Logger retrievalServiceLogger() {
        return (Logger) LoggerFactory.getLogger(RetrievalService.class);
    }
}
