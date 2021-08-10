package com.zafar.homeGenie.scraper;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.zafar.homeGenie.domain.ScrapeRequest;
import com.zafar.homeGenie.utils.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class Scraper implements HandlerFunction<ServerResponse> {
    private static Logger logger = LogManager.getLogger(Scraper.class);

    @Autowired
    private CrawlRepository crawlRepository;

    protected Mono<ServerResponse> scrape(Mono<ScrapeRequest> scrapeRequestMono) {
        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setDownloadImages(true);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setCssEnabled(true);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setUseInsecureSSL(true);
        webClient.getOptions().setRedirectEnabled(true);
        webClient.setWebConnection(new HttpWebConnectionWrapper(webClient));
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        Map<String, Object> context = new HashMap<>();
        return scrapeRequestMono.flatMap(scrapeRequest -> {
            WebsiteCrawl crawl = crawlRepository.getCrawl(scrapeRequest.getCrawlId());
            for (Map.Entry<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> crawlStep :
                    crawl.getModusOperandi().entrySet()) {
                Website.WebPage webPage = crawlStep.getKey();
                Page page= null;
                try {
                    if(webPage.getAddress()!=null && !webPage.getAddress().isEmpty()) {
                        page = webClient.getPage(new URL(webPage.getAddress()));
                        context.put(Constants.CURRENT_PAGE, page);
                    } else {
                        page = (Page) context.get(Constants.CURRENT_PAGE);
                    }
                } catch (IOException e) {
                    logger.error("Issue with URL", e);
                    return Mono.error(e);
                }
                if (page.isHtmlPage()) {
                    crawlStep.getValue().apply(context);
                    logger.info("page {} loaded", crawlStep.getKey().getName());
                }

            }
            return ServerResponse.ok().body(BodyInserters.fromValue(context.getOrDefault(Constants.RESULT, "")));
        });
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest serverRequest) {
        try {
            return scrape(serverRequest.bodyToMono(ScrapeRequest.class));
        } catch (Exception e) {
            logger.error(e);
            return Mono.error(e);
        }
    }
}
