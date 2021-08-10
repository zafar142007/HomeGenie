package com.zafar.homeGenie.domain;

public class ScrapeRequest {
    private String crawlId;

    public ScrapeRequest() {
    }

    public ScrapeRequest(String crawlId) {
        this.crawlId = crawlId;
    }

    public String getCrawlId() {
        return crawlId;
    }

    public ScrapeRequest setCrawlId(String crawlId) {
        this.crawlId = crawlId;
        return this;
    }
}
