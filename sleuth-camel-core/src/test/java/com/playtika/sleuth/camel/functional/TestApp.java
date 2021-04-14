/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Playtika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.playtika.sleuth.camel.functional;

import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.apache.camel.LoggingLevel.INFO;

@SpringBootApplication
@Configuration
public class TestApp {

    static final String DIRECT_ROUTE_URI = "direct:directRoute";
    static final String ASYNC_DIRECT_ROUTE_URI = "direct:asyncDirectRoute";
    static final String MOCK_DIRECT_ROUTE_TO_URI = "mock:directRouteTo";
    static final String MOCK_EXCEPTION_ROUTE_TO_URI = "mock:exceptionRouteTo";
    static final String DIRECT_ROUTE_ID = "directRoute";
    static final String ASYNC_DIRECT_ROUTE_ID = "asyncDirectRoute";

    @Bean
    public Sampler alwaysSampler() {
        return Sampler.ALWAYS_SAMPLE;
    }

    @Bean
    public TestSpanHandler testSpanHandler() {
        return new TestSpanHandler();
    }

    @Bean
    public SomeTracedService someService() {
        return new SomeTracedService();
    }

    @Bean
    public RoutesBuilder camelRoutes(SomeTracedService someTracedService, Processor mockProcessor) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(RuntimeException.class)
                        .log(INFO, "Exception caught. Processing fallback...")
                        .to(MOCK_EXCEPTION_ROUTE_TO_URI)
                        .routeId("exceptionRoute");

                from(DIRECT_ROUTE_URI)
                        .log(INFO, "Message is going to be processed...")
                        .process(mockProcessor)
                        .process(exchange -> someTracedService.newSpanMethod())
                        .to(MOCK_DIRECT_ROUTE_TO_URI)
                        .routeId(DIRECT_ROUTE_ID);

                from(ASYNC_DIRECT_ROUTE_URI)
                        .log(INFO, "Message is going to be processed in async way...")
                        .process(mockProcessor)
                        .process(exchange -> someTracedService.newSpanMethod())
                        .threads()
                        .to(MOCK_DIRECT_ROUTE_TO_URI)
                        .routeId(ASYNC_DIRECT_ROUTE_ID);
            }
        };
    }

    @Slf4j
    public static class SomeTracedService {
        @NewSpan
        void newSpanMethod() {
            log.info("Some service has been invoked.");
        }
    }

}
