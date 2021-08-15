package com.zafar.homeGenie.scraper;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.function.Function;

public class WebsiteCrawl {

    private List<CrawlStep> modusOperandi = new LinkedList<>();
    private CrawlContext crawlContext = new CrawlContext();
    @JsonIgnore
    private String crawlId;

    public WebsiteCrawl() {
        crawlId = UUID.randomUUID().toString();
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void cleanup() {

    }

    public void addCrawlStep(CrawlStep crawlStep) {
        this.modusOperandi.add(crawlStep);
    }

    public List<CrawlStep> getModusOperandi() {
        return modusOperandi;
    }

    public CrawlContext getCrawlContext() {
        return crawlContext;
    }

    public abstract class CrawlFunction implements Function<Void, Void> {
        public CrawlContext getContext() {
            return getCrawlContext();
        }

        public abstract Void apply(Void unused);
    }

    public static class CrawlContext {
        private Map<String, Object> contextMap = new LinkedHashMap<>();

        public CrawlContext() {
        }

        public Map<String, Object> getContextMap() {
            return contextMap;
        }

        public CrawlContext setContextMap(Map<String, Object> contextMap) {
            this.contextMap = contextMap;
            return this;
        }
    }

}
