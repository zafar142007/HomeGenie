package com.zafar.homeGenie.repository;

import com.zafar.homeGenie.scraper.WebsiteCrawl;

public abstract class CrawlProvider {
    public abstract WebsiteCrawl provide();

    public abstract String getId();
}
