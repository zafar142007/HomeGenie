package com.zafar.homeGenie.scraper;

import com.gargoylesoftware.htmlunit.HttpWebConnection;
import com.gargoylesoftware.htmlunit.WebClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class HttpWebConnectionWrapper extends HttpWebConnection {
    private static Logger logger = LogManager.getLogger(HttpWebConnectionWrapper.class);

    /**
     * Creates a new HTTP web connection instance.
     *
     * @param webClient the WebClient that is using this connection
     */
    public HttpWebConnectionWrapper(WebClient webClient) {
        super(webClient);
    }

    @Override
    protected HttpClientBuilder createHttpClientBuilder() {
        HttpClientBuilder builder  = super.createHttpClientBuilder();
        builder.setRetryHandler(new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount,
                                        HttpContext context) {
                if (executionCount > 3) {
                    logger.warn("Maximum tries reached for client http pool ");
                    return false;
                }
                if (exception instanceof org.apache.http.NoHttpResponseException) {
                    logger.warn("No response from server on " + executionCount + " call");
                    return true;
                }
                return false;
            }
        });
        return builder;
    }
}
