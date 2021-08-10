package com.zafar.homeGenie.repository;

import com.zafar.homeGenie.scraper.CrawlRepository;
import com.zafar.homeGenie.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class AmazonCrawl {

    @Autowired
    private CrawlRepository crawlRepository;

    @PostConstruct
    public void init(){
       // crawlRepository.put(Constants.AMAZON_PRICE, )

    }
}
