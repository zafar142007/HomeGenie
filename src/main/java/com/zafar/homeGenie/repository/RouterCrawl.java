package com.zafar.homeGenie.repository;

import com.gargoylesoftware.htmlunit.html.*;
import com.zafar.homeGenie.scraper.CrawlRepository;
import com.zafar.homeGenie.scraper.Website;
import com.zafar.homeGenie.scraper.WebsiteCrawl;
import com.zafar.homeGenie.utils.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class RouterCrawl extends CrawlProvider {
    private final static Logger logger = LogManager.getLogger(RouterCrawl.class);

    @Autowired
    private CrawlRepository crawlRepository;

    @PostConstruct
    public void init(){
        crawlRepository.putCrawl(getId(), this);
    }

    @Override
    public WebsiteCrawl provide() {
        LinkedHashMap<Website.CrawlTask, Function<Map<String, Object>, Map<String, Object>>> crawl = new LinkedHashMap<>();
        crawlLoginPage(crawl);
        crawlHomePage(crawl);
        crawlStatsPage("stats1", crawl);
        crawlStatsPage("stats2", crawl);//crawl again to find the difference in data consumed
        findifTVIdle(crawl);//if the difference is high then TV is in use otherwise no
        askAlexaToSwitchOff(crawl); //if not in use switch it off
        return new WebsiteCrawl(crawl);
    }

    @Override
    public String getId() {
        return Constants.ROUTER_TV_POWER;
    }

    private void findifTVIdle(LinkedHashMap<Website.CrawlTask, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.CrawlTask analysis = new Website.CrawlTask();
        analysis.setName("analysis");
        crawl.put(analysis, map -> {
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
            if (avgRx < Constants.RX_THRESHOLD_BYTES && avgTx < Constants.TX_THRESHOLD_BYTES) {
                map.put(Constants.SHOULD_TV_BE_OFF, true);
            } else {
                map.put(Constants.SHOULD_TV_BE_OFF, false);
            }
            return map;
        });
    }

    private void askAlexaToSwitchOff(LinkedHashMap<Website.CrawlTask, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.CrawlTask alexa = new Website.CrawlTask();
        alexa.setName("alexa");
        crawl.put(alexa, map -> {
            if ((Boolean) (map.get(Constants.SHOULD_TV_BE_OFF)) == true) {
                logger.info("turning off tv");
                map.put(Constants.RESULT, true);
            } else {
                map.put(Constants.RESULT, false);
            }
            HtmlPage htmlPage = (HtmlPage) map.get(Constants.CURRENT_PAGE);
            htmlPage.getWebClient().close();
            return map;
        });
    }

    private void crawlStatsPage(String name, LinkedHashMap<Website.CrawlTask, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.CrawlTask stats = new Website.CrawlTask();
        stats.setName(name);
        crawl.put(stats, map -> {
            HtmlPage htmlPage = (HtmlPage) map.get(Constants.CURRENT_PAGE);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                logger.error(e);
            }
            try {
                List<DomNode> rows = htmlPage.querySelectorAll(".nw-mini-table-tr");
                for (DomNode row : rows) {
                    if (row.getVisibleText().toLowerCase().contains(Constants.TARGET_DEVICE)) {
                        HtmlTableRow tableRow = (HtmlTableRow) row;
                        tableRow.click();
                        break;
                    }
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
                        if(r!=null) {
                            r.putAll(result);
                        } else {
                            r=result;
                        }
                        map.put(Constants.RESULT, r);
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error(e);
                htmlPage.getWebClient().close();
            } finally {

            }
            return map;
        });
    }

    private void crawlHomePage(LinkedHashMap<Website.CrawlTask, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.CrawlTask homePage = new Website.CrawlTask();
        homePage.setName("home");

        crawl.put(homePage, map -> {
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
            } catch (IOException e) {
                logger.error(e);
                htmlPage.getWebClient().close();
            } finally {

            }
            return map;
        });
    }

    private void crawlLoginPage(LinkedHashMap<Website.CrawlTask, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.CrawlTask loginPage = new Website.CrawlTask();
        loginPage.setName("login");
        loginPage.setAddress("http://192.168.0.1");
        crawl.put(loginPage, map -> {
            HtmlPage htmlPage = (HtmlPage) map.get(Constants.CURRENT_PAGE);
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
                htmlPage.getWebClient().close();
            }
            return map;
        });
    }
}
