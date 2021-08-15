package com.zafar.homeGenie.scraper;

public class CrawlStep {
    private String name;
    private WebsiteCrawl.CrawlFunction crawlFunction;

    public CrawlStep(String name, WebsiteCrawl.CrawlFunction crawlFunction) {
        this.name = name;
        this.crawlFunction = crawlFunction;
    }

    public String getName() {
        return name;
    }

    public CrawlStep setName(String name) {
        this.name = name;
        return this;
    }

    public WebsiteCrawl.CrawlFunction getCrawlFunction() {
        return crawlFunction;
    }

    public CrawlStep setCrawlFunction(WebsiteCrawl.CrawlFunction crawlFunction) {
        this.crawlFunction = crawlFunction;
        return this;
    }


}

