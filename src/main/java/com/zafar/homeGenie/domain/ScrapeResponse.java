package com.zafar.homeGenie.domain;

import java.util.Map;

public class ScrapeResponse {
    private Map<String, Object> result;

    public Map<String, Object> getResult() {
        return result;
    }

    public ScrapeResponse setResult(Map<String, Object> result) {
        this.result = result;
        return this;
    }

    public ScrapeResponse(Map<String, Object> result) {
        this.result = result;
    }
}
