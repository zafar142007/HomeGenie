package com.zafar.homeGenie.scraper;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class WebsiteCrawl {

    private LinkedHashMap<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> modusOperandi;
    @JsonIgnore
    private String crawlId;

    public WebsiteCrawl(LinkedHashMap<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> modusOperandi) {
        this.modusOperandi = modusOperandi;
        crawlId = UUID.randomUUID().toString();
    }

    public String getCrawlId() {
        return crawlId;
    }

    public LinkedHashMap<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> getModusOperandi() {
        return modusOperandi;
    }

}
