package com.zafar.homeGenie.scraper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CrawlRepository {
    private final static Logger logger = LogManager.getLogger(CrawlRepository.class);
    private Map<String, WebsiteCrawl> crawlMap = new HashMap<>();

    public WebsiteCrawl getCrawl(String crawlId) {
        return crawlMap.get(crawlId);
    }
    public void putCrawl(String crawlId, WebsiteCrawl websiteCrawl){
        crawlMap.put(crawlId, websiteCrawl);
    }
}
