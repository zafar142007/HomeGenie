package com.zafar.homeGenie.scraper;

import com.zafar.homeGenie.repository.CrawlProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CrawlRepository {
    private final static Logger logger = LogManager.getLogger(CrawlRepository.class);
    private Map<String, CrawlProvider> crawlMap = new HashMap<>();

    public CrawlProvider getCrawl(String crawlId) {
        return crawlMap.get(crawlId);
    }
    public void putCrawl(String crawlId, CrawlProvider crawlProvider){
        crawlMap.put(crawlId, crawlProvider);
    }
}
