package com.zafar.homeGenie.config;

import com.zafar.homeGenie.scraper.Scraper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@Configuration
public class AppConfig {
    @Bean
    public RouterFunction<ServerResponse> scrape(@Autowired Scraper scraper) {
        return RouterFunctions.route(POST("/scrape"), request ->
                scraper.handle(request)
        );
    }
}
