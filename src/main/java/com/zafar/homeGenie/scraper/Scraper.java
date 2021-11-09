package com.zafar.homeGenie.scraper;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.zafar.homeGenie.domain.ScrapeRequest;
import com.zafar.homeGenie.domain.ScrapeResponse;
import com.zafar.homeGenie.utils.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class Scraper implements HandlerFunction<ServerResponse> {
    private static Logger logger = LogManager.getLogger(Scraper.class);

    @Autowired
    private CrawlRepository crawlRepository;

    public Mono<ScrapeResponse> scrape(Mono<ScrapeRequest> scrapeRequestMono) {
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
        return scrapeRequestMono.flatMap(scrapeRequest -> {
            Mono<ScrapeResponse> response = null;
            WebsiteCrawl crawl = crawlRepository.getCrawl(scrapeRequest.getCrawlId()).provide();
            crawl.getCrawlContext().getContextMap().put(Constants.WEBCLIENT, webClient);
            Map<String, Object> context = crawl.getCrawlContext().getContextMap();
            for (CrawlStep crawlStep : crawl.getModusOperandi()) {
                logger.info("step {} loading", crawlStep.getName());
                crawlStep.getCrawlFunction().apply(null);
                logger.info("step {} loaded", crawlStep.getName());
                if (context.get(Constants.ABORT) != null && context.get(Constants.ABORT) == Boolean.TRUE) {
                    response = Mono.just(new ScrapeResponse()
                            .addField(Constants.ABORT_REASON, context.getOrDefault(Constants.ABORT_REASON, "Unexpected error occurred")));
                    logger.error("Aborting {}", context.get(Constants.ABORT_REASON));
                    break;
                }
            }
            webClient.close();
            if (response == null) {
                response = Mono.just(new ScrapeResponse().addField(Constants.RESULT, context.getOrDefault(Constants.RESULT, "")));
            }
            return response;
        });
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest serverRequest) {
        try {
            Mono<ScrapeResponse> scrapeResponseMono = scrape(serverRequest.bodyToMono(ScrapeRequest.class));
            Mono<ServerResponse> r = scrapeResponseMono.flatMap((resp) -> {
                Mono<ServerResponse> response;
                if (StringUtils.isEmpty((String) resp.getResult().get(Constants.ABORT_REASON))) {
                    response = ServerResponse.status(HttpStatus.OK)
                            .bodyValue(resp);
                } else {
                    response = ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .bodyValue(resp);
                }
                logger.info("http status result: {}", resp);
                return response;
            });
            return r;
        } catch (Exception e) {
            logger.error(e);
            return Mono.error(e);
        }
    }
}
