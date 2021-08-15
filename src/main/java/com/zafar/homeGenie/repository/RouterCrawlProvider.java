package com.zafar.homeGenie.repository;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import com.zafar.homeGenie.scraper.CrawlRepository;
import com.zafar.homeGenie.scraper.CrawlJob;
import com.zafar.homeGenie.scraper.CrawlStep;
import com.zafar.homeGenie.scraper.WebsiteCrawl;
import com.zafar.homeGenie.utils.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.util.*;

@Component
public class RouterCrawlProvider extends CrawlProvider {
    private final static Logger logger = LogManager.getLogger(RouterCrawlProvider.class);

    @Autowired
    private CrawlRepository crawlRepository;

    @PostConstruct
    public void init() {
        crawlRepository.putCrawl(getId(), this);
    }

    @Override
    public WebsiteCrawl provide() {
        WebsiteCrawl crawl = new WebsiteCrawl();
        crawlLoginPage(crawl);
        crawlHomePage(crawl);
        crawlStatsPage(crawl);
        crawlStatsPage(crawl);//crawl again to find the difference in data consumed
        findifTVIdle(crawl);//if the difference is high then TV is in use otherwise no
        askAlexaToSwitchOff(crawl); //if not in use switch it off
        return crawl;
    }

    @Override
    public String getId() {
        return Constants.ROUTER_TV_POWER;
    }

    private void findifTVIdle(WebsiteCrawl crawl) {
        CrawlJob.CrawlTask analysis = new CrawlJob.CrawlTask();
        analysis.setName("analysis");
        WebsiteCrawl.CrawlFunction idleCheckFunction = crawl.new CrawlFunction() {

            @Override
            public Void apply(Void unused) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                Map<Long, Map<String, Object>> readings = (Map<Long, Map<String, Object>>) map.get(Constants.RESULT);
                Long lastTimestamp = null;
                double lastRx = 0, lastTx = 0, diffTime = 0, diffRx = 0, diffTx = 0, totalDiffRx = 0, totalDiffTx = 0, avgRx = 0, avgTx = 0;
                for (Map.Entry<Long, Map<String, Object>> entry : readings.entrySet()) {
                    Long timestamp = entry.getKey();
                    if (lastTimestamp == null) {
                        lastTimestamp = timestamp;
                        lastRx = (Double) entry.getValue().get("Rx");
                        lastTx = (Double) entry.getValue().get("Tx");
                    } else {
                        diffTime = (timestamp - lastTimestamp) / 1000.0;
                        diffRx = (Double) entry.getValue().get("Rx") - lastRx;
                        diffTx = (Double) entry.getValue().get("Tx") - lastTx;
                        totalDiffRx = totalDiffRx + (diffRx / diffTime);
                        totalDiffTx = totalDiffTx + (diffTx / diffTime);
                    }
                }
                avgRx = totalDiffRx / (readings.size() - 1);
                avgTx = totalDiffTx / (readings.size() - 1);
                logger.info("avgRx {} B/s, avgTx {} B/s", avgRx, avgTx);
                if (avgRx < Constants.RX_THRESHOLD_BYTES && avgTx < Constants.TX_THRESHOLD_BYTES) {
                    map.put(Constants.SHOULD_TV_BE_OFF, true);
                } else {
                    map.put(Constants.SHOULD_TV_BE_OFF, false);
                }
                return null;
            }
        };
        CrawlStep idleCheck = new CrawlStep("idleChecke", idleCheckFunction);
        crawl.addCrawlStep(idleCheck);
    }

    private void askAlexaToSwitchOff(WebsiteCrawl crawl) {
        WebsiteCrawl.CrawlFunction alexa = crawl.new CrawlFunction() {

            @Override
            public Void apply(Void unused) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                if ((Boolean) (map.get(Constants.SHOULD_TV_BE_OFF)) == true) {
                    logger.info("turning off tv");
                    map.put(Constants.RESULT, true);
                } else {
                    map.put(Constants.RESULT, false);
                }
                return null;
            }
        };
        CrawlStep instructAlexa = new CrawlStep("alexa", alexa);
        crawl.addCrawlStep(instructAlexa);
    }

    private void crawlStatsPage(WebsiteCrawl crawl) {
        WebsiteCrawl.CrawlFunction statsPageFunction = crawl.new CrawlFunction() {

            @Override
            public Void apply(Void unused) {
                WebsiteCrawl.CrawlContext crawlContext = getContext();
                Map<String, Object> map = crawlContext.getContextMap();
                HtmlPage htmlPage = (HtmlPage) map.get(Constants.CURRENT_PAGE);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    logger.error(e);
                }
                try {
                    boolean flag = false;
                    List<DomNode> rows = htmlPage.querySelectorAll(".nw-mini-table-tr");
                    for (DomNode row : rows) {
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
                    Thread.sleep(5000);
                    List<DomNode> tiles = htmlPage.querySelectorAll("span.info");
                    for (DomNode tile : tiles) {
                        if ((tile.getVisibleText()).contains("byte")) {
                            Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
                            Map<String, Object> result1 = new HashMap<>();
                            if (tile.getVisibleText().split("/")[0].contains("Kbyte")) {
                                result1.put("Rx", Double.parseDouble(tile.getVisibleText().split("/")[0].split(" ")[0]) * 1000);
                            } else if (tile.getVisibleText().split("/")[0].contains("Mbyte")) {
                                result1.put("Rx", Double.parseDouble(tile.getVisibleText().split("/")[0].split(" ")[0]) * 1000000);
                            }
                            if (tile.getVisibleText().split("/")[1].contains("Kbyte")) {
                                result1.put("Tx", Double.parseDouble(tile.getVisibleText().split("/")[1].split(" ")[0]) * 1000);
                            } else if (tile.getVisibleText().split("/")[1].contains("Mbyte")) {
                                result1.put("Tx", Double.parseDouble(tile.getVisibleText().split("/")[1].split(" ")[0]) * 1000000);
                            }
                            result.put(System.currentTimeMillis(), result1);
                            Map<Long, Map<String, Object>> r = (Map<Long, Map<String, Object>>) (map.get(Constants.RESULT));
                            if (r != null) {
                                r.putAll(result);
                            } else {
                                r = result;
                            }
                            map.put(Constants.RESULT, r);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error(e);
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
                    Thread.sleep(3000);
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
                    Thread.sleep(3000);
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
