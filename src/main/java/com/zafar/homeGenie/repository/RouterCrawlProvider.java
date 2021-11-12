package com.zafar.homeGenie.repository;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import com.zafar.homeGenie.domain.FixedSizeMapStorage;
import com.zafar.homeGenie.domain.ScrapeRequest;
import com.zafar.homeGenie.domain.ScrapeResponse;
import com.zafar.homeGenie.scraper.*;
import com.zafar.homeGenie.utils.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.*;

@Component
public class RouterCrawlProvider extends CrawlProvider {
    private final static Logger logger = LogManager.getLogger(RouterCrawlProvider.class);

    @Autowired
    private CrawlRepository crawlRepository;

    private Map<String, Object> storage = new HashMap<>();

    @Autowired
    private Scraper scraper;

    @Value("${" + Constants.ROUTER_TV_POWER + ".tv.off.url}")
    private String tvOffUrl;

    @Value("${" + Constants.ROUTER_TV_POWER + ".crawl.schedule}")
    private String crawlSchedule;

    private String key;

    private ThreadPoolTaskScheduler executorService = new ThreadPoolTaskScheduler();

    @PostConstruct
    public void init() {
        crawlRepository.putCrawl(getId(), this);
        storage.put(Constants.DATA_METRICS, new FixedSizeMapStorage<Long, Map<String, Double>, TreeMap<Long, Map<String, Double>>>
                (new TreeMap<>(Comparator.reverseOrder()), Constants.NUMBER_OF_CONSECUTIVE_DETECTONS + 1));// for 1 detection 2 measurements needed
        if (StringUtils.isNotEmpty(crawlSchedule)) {
            scheduleCrawl();
        }
    }

    private void scheduleCrawl() {

        executorService.setPoolSize(2);
        executorService.initialize();
        executorService.schedule(() -> {
            logger.info("starting scheduled crawl");
            ScrapeRequest request = new ScrapeRequest(Constants.ROUTER_TV_POWER);
            Mono<ScrapeRequest> requestMono = Mono.just(request);

            Mono<ScrapeResponse> responseMono = scraper.scrape(requestMono);
            Mono<ServerResponse> r = responseMono.flatMap((resp) -> {
                Mono<ServerResponse> response;
                if (StringUtils.isEmpty((String) resp.getResult().get(Constants.ABORT_REASON))) {
                    response = ServerResponse.status(HttpStatus.OK)
                            .bodyValue(resp);
                } else {
                    response = ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .bodyValue(resp);
                }
                logger.info("cron http status result: {}", resp);
                return response;

            });
            r.block();
            logger.info("finished scheduled crawl");

        }, new CronTrigger(crawlSchedule));
        logger.info("scheduled crawl at frequency {}", crawlSchedule);
    }

    @Override
    public WebsiteCrawl provide() {
        WebsiteCrawl crawl = new WebsiteCrawl();
        crawlLoginPage(crawl);
        crawlHomePage(crawl);
        crawlStatsPage(crawl);
        findIfTVIdle(crawl);//if the difference is high then TV is in use otherwise no
        switchOff(crawl); //if not in use switch it off
        return crawl;
    }

    @Override
    public String getId() {
        return Constants.ROUTER_TV_POWER;
    }

    private void findIfTVIdle(WebsiteCrawl crawl) {
        CrawlJob.CrawlTask analysis = new CrawlJob.CrawlTask();
        analysis.setName("analysis");
        WebsiteCrawl.CrawlFunction idleCheckFunction = crawl.new CrawlFunction() {

            @Override
            public Void apply(Void unused) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                Set<Map.Entry<Long, Map<String, Double>>> readings =
                        ((FixedSizeMapStorage<Long, Map<String, Double>, TreeMap<Long, Map<String, Double>>>)
                                storage.get(Constants.DATA_METRICS)).getEntrySet();

                Long lastTimestamp = null;
                int consecutiveDetections = 0;
                double lastRx = 0, lastTx = 0, diffTime = 0, diffRx = 0, diffTx = 0, rxRate = 0, txRate = 0, avgRx = 0, avgTx = 0;
                for (Map.Entry<Long, Map<String, Double>> entry : readings) {
                    Long timestamp = entry.getKey();
                    if (lastTimestamp == null) {
                        lastTimestamp = timestamp;
                        lastRx = entry.getValue().get("Rx");
                        lastTx = entry.getValue().get("Tx");
                    } else {
                        diffTime = (lastTimestamp - timestamp) / 1000.0;
                        diffRx = lastRx - entry.getValue().get("Rx");
                        diffTx = lastTx - entry.getValue().get("Tx");
                        rxRate = (diffRx / diffTime);
                        txRate = (diffTx / diffTime);
                        lastTimestamp = timestamp;
                        lastRx = entry.getValue().get("Rx");
                        lastTx = entry.getValue().get("Tx");
                        logger.info("timestamp {}, rxRate {} B/s, txRate {} B/s", lastTimestamp, rxRate, txRate);
                        if (isTvOff(rxRate, txRate)) {
                            consecutiveDetections++;
                        } else break;
                    }
                }
                logger.info("consecutive detections {}", consecutiveDetections);
                if (consecutiveDetections >= Constants.NUMBER_OF_CONSECUTIVE_DETECTONS - 1) {
                    map.put(Constants.SHOULD_TV_BE_OFF, Boolean.TRUE);
                } else {
                    map.put(Constants.SHOULD_TV_BE_OFF, Boolean.FALSE);
                }
                return null;
            }
        };
        CrawlStep idleCheck = new CrawlStep("idleCheck", idleCheckFunction);
        crawl.addCrawlStep(idleCheck);
    }

    private boolean isTvOff(double rxRate, double txRate) {
        if (rxRate < Constants.RX_THRESHOLD_BYTES && txRate < Constants.TX_THRESHOLD_BYTES) {
            logger.info("TV should be off");
            return true;
        } else {
            logger.info("TV should not be off");
            return false;
        }
    }


    private void switchOff(WebsiteCrawl crawl) {
        WebsiteCrawl.CrawlFunction turningOffTv = crawl.new CrawlFunction() {

            @Override
            public Void apply(Void unused) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                if ((map.get(Constants.SHOULD_TV_BE_OFF)) == Boolean.TRUE) {
                    logger.info("turning off tv");
                    String key = readKey();
                    tvOffUrl = tvOffUrl.replace("[key]", key);
                    commandToTurnOff(map);
                } else {
                    map.put(Constants.RESULT, false);
                }
                return null;
            }
        };
        CrawlStep instructAlexa = new CrawlStep("turnOffTv", turningOffTv);
        crawl.addCrawlStep(instructAlexa);
    }

    private void commandToTurnOff(Map<String, Object> map) {
        org.springframework.web.reactive.function.client.WebClient webClient = org.springframework.web.reactive.function.client.WebClient.builder()
                .baseUrl(tvOffUrl)
                .build();
        ResponseEntity<String> response = null;
        try {
            Mono<ResponseEntity<String>> responseMono = webClient.get().retrieve().toEntity(String.class);
            response = responseMono.block(Duration.ofSeconds(10));
        } catch (Exception e) {
            logger.error("failed to trigger switch off", e);
            map.put(Constants.RESULT, false);
        }
        if (response != null && response.getStatusCode() == HttpStatus.OK) {
            map.put(Constants.RESULT, true);
            logger.info("successfully triggered switch off");
        } else {
            map.put(Constants.RESULT, false);
            logger.error("failed to trigger switch off");
        }

    }

    private String readKey() {
        if (StringUtils.isEmpty(key)) {
            InputStream keyFile = getClass().getClassLoader().getResourceAsStream("key.txt");
            Scanner scanner = new Scanner(keyFile);
            key = scanner.next();
            return key;
        } else return key;
    }

    private void crawlStatsPage(WebsiteCrawl crawl) {
        WebsiteCrawl.CrawlFunction statsPageFunction = crawl.new CrawlFunction() {

            @Override
            public Void apply(Void unused) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                HtmlPage htmlPage = (HtmlPage) map.get(Constants.CURRENT_PAGE);
                try {
                    Thread.sleep(Constants.WAIT_TIME_BETWEEN_CHECKS_MS);
                } catch (InterruptedException e) {
                    logger.error(e);
                }
                try {
                    boolean flag = false;
                    List<DomNode> rows = htmlPage.querySelectorAll(".nw-mini-table-tr");
                    for (DomNode row : rows) {
                        logger.info("row text: {}", row.getVisibleText());
                        if (row.getVisibleText().toLowerCase().contains(Constants.TARGET_DEVICE)) {
                            HtmlTableRow tableRow = (HtmlTableRow) row;
                            tableRow.click();
                            flag = true;
                            break;
                        }
                    }
                    if (!flag) {
                        logger.info("Target device not found, aborting");
                        map.put(Constants.ABORT, true);
                        map.put(Constants.ABORT_REASON, "Target device not found");
                        return null;
                    }
                    Thread.sleep(20000);
                    Map<String, Double> byteConversion = new HashMap<>();
                    byteConversion.put("Kbyte", 1000.0);
                    byteConversion.put("Mbyte", 1000000.0);
                    byteConversion.put("Gbyte", 1000000000.0);
                    List<DomNode> tiles = htmlPage.querySelectorAll("span.info");
                    for (DomNode tile : tiles) {

                        if ((tile.getVisibleText()).contains("Active")) { //wifi power saving mode is Active
                            //TV is already off
                            map.put(Constants.ABORT, true);
                            map.put(Constants.ABORT_REASON, "Power saving mode on");
                        }

                        if ((tile.getVisibleText()).contains("byte")) { // something like "111.3 Kbyte/121.0 MByte"
                            Map<String, Double> dataTransferMetrics = new HashMap<>();
                            String unit = tile.getVisibleText().split("/")[0].split(" ")[1];
                            dataTransferMetrics.put("Rx", Double.parseDouble(tile.getVisibleText().split("/")[0].split(" ")[0]) * byteConversion.get(unit)); //put in bytes
                            unit = tile.getVisibleText().split("/")[1].split(" ")[1];
                            dataTransferMetrics.put("Tx", Double.parseDouble(tile.getVisibleText().split("/")[1].split(" ")[0]) * byteConversion.get(unit)); //in bytes
                            FixedSizeMapStorage<Long, Map<String, Double>, TreeMap<Long, Map<String, Double>>> metrics =
                                    (FixedSizeMapStorage<Long, Map<String, Double>, TreeMap<Long, Map<String, Double>>>) storage.get(Constants.DATA_METRICS);
                            metrics.put(System.currentTimeMillis(), dataTransferMetrics);
                            logger.info("stats {}", dataTransferMetrics);
                        }
                    }
                } catch (Exception e) {
                    logger.error(e);
                    e.printStackTrace();
                    map.put(Constants.ABORT, true);
                    map.put(Constants.ABORT_REASON, e.getMessage());
                }
                return null;
            }
        };
        CrawlStep crawlStatsPage = new CrawlStep("stats", statsPageFunction);
        crawl.addCrawlStep(crawlStatsPage);
    }

    private void crawlHomePage(WebsiteCrawl crawl) {

        WebsiteCrawl.CrawlFunction homePageFunction = crawl.new CrawlFunction() {
            @Override
            public Void apply(Void v) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                HtmlPage htmlPage = (HtmlPage) map.get(Constants.CURRENT_PAGE);
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    logger.error(e);
                }
                try {
                    List<DomNode> rows = htmlPage.querySelectorAll("span.menu_item");
                    HtmlPage page = null;
                    for (DomNode row : rows) {
                        if (row.getVisibleText().toLowerCase().contains("wi-fi")) {
                            HtmlSpan span = (HtmlSpan) row;
                            page = span.click();
                            logger.info("Span clicked");
                            List<DomNode> anchors = page.querySelectorAll("a.menu_item");
                            for (DomNode anchor : anchors) {
                                if (anchor.getVisibleText().toLowerCase().contains("client management")) {
                                    page = ((HtmlAnchor) anchor).click();
                                    logger.info("Anchor clicked");
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    map.put(Constants.CURRENT_PAGE, page);
                } catch (Exception e) {
                    logger.error(e);
                    map.put(Constants.ABORT, true);
                    map.put(Constants.ABORT_REASON, e.getMessage());
                }
                return null;
            }
        };
        CrawlStep crawlHomePage = new CrawlStep("home", homePageFunction);
        crawl.addCrawlStep(crawlHomePage);

    }

    private void crawlLoginPage(WebsiteCrawl crawl) {
        WebsiteCrawl.CrawlFunction loginFunction = crawl.new CrawlFunction() {
            @Override
            public Void apply(Void c) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                WebClient webClient = (WebClient) map.get(Constants.WEBCLIENT);
                HtmlPage htmlPage = null;
                try {
                    htmlPage = webClient.getPage(new URL("http://192.168.0.1"));
                } catch (IOException e) {
                    e.printStackTrace();
                    map.put(Constants.ABORT, true);
                    map.put(Constants.ABORT_REASON, e.getMessage());
                    return null;
                }

                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    logger.error(e);
                }
                HtmlTextInput name = htmlPage.querySelector("[name=login_name]");
                HtmlPasswordInput pass = htmlPage.querySelector("[name=login_password]");
                List<DomNode> buttons = htmlPage.querySelectorAll("[type=submit]");
                try {
                    name.type("admin");
                    pass.type("password");
                    HtmlPage homePage = null;
                    for (DomNode node : buttons) {
                        if (node instanceof HtmlButton && "submit".equals(((HtmlButton) node).getAttribute("type")) && "Login".equals(node.getVisibleText())) {
                            homePage = ((HtmlButton) node).click();
                            map.put(Constants.CURRENT_PAGE, homePage);
                            break;
                        }
                    }
                    logger.info("logged in");
                } catch (IOException e) {
                    logger.error(e);
                    map.put(Constants.ABORT, true);
                    map.put(Constants.ABORT_REASON, e.getMessage());
                }
                return null;
            }
        };
        CrawlStep crawlLoginPage = new CrawlStep("login", loginFunction);
        crawl.addCrawlStep(crawlLoginPage);
    }
}
