package com.zafar.homeGenie.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ScrapeResponse {
    private Map<String, Object> result;
    private final Instant timestamp = Instant.now();

    public ScrapeResponse() {
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ScrapeResponse setResult(Map<String, Object> result) {
        this.result = result;
        return this;
    }

    public ScrapeResponse addField(String key, Object value) {
        if (result == null) {
            result = new HashMap<>();
        }
        result.put(key, value);
        return this;
    }

    public ScrapeResponse(Map<String, Object> result) {
        this.result = result;
    }
}
