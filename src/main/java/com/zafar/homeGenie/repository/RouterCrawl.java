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
public class RouterCrawl {
    private final static Logger logger = LogManager.getLogger(RouterCrawl.class);

    @Autowired
    private CrawlRepository crawlRepository;

    @PostConstruct
    public void init() {
        LinkedHashMap<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> crawl = new LinkedHashMap<>();
        crawlLoginPage(crawl);
        crawlHomePage(crawl);
        crawlStatsPage(crawl);

        WebsiteCrawl websiteCrawl = new WebsiteCrawl(crawl);
        crawlRepository.putCrawl(Constants.ROUTER_TV_POWER, websiteCrawl);

    }

    private void crawlStatsPage(LinkedHashMap<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.WebPage stats = new Website.WebPage();
        stats.setName("stats");
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
                    if ((tile.getVisibleText()).contains("Mbyte")) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("Rx", tile.getVisibleText().split("/")[0]);
                        result.put("Tx", tile.getVisibleText().split("/")[1]);
                        result.put("timestamp", System.currentTimeMillis());
                        map.put(Constants.RESULT, result);
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error(e);
            } finally {
                htmlPage.getWebClient().close();
            }
            return map;
        });
    }

    private void crawlHomePage(LinkedHashMap<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.WebPage homePage = new Website.WebPage();
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

    private void crawlLoginPage(LinkedHashMap<Website.WebPage, Function<Map<String, Object>, Map<String, Object>>> crawl) {
        Website.WebPage loginPage = new Website.WebPage();
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
