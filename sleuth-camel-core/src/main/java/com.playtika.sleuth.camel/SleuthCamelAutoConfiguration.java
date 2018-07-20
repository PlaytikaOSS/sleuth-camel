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

package com.playtika.sleuth.camel;

import lombok.AllArgsConstructor;
import org.apache.camel.CamelContext;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.messaging.HeaderBasedMessagingExtractor;
import org.springframework.cloud.sleuth.instrument.messaging.HeaderBasedMessagingInjector;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapExtractor;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapInjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AllArgsConstructor
@Configuration
@ConditionalOnBean(Tracer.class)
@ConditionalOnClass({CamelContext.class})
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.sleuth.camel.enabled", matchIfMissing = true)
public class SleuthCamelAutoConfiguration {

    private final CamelContext camelContext;
    private final Tracer tracer;

    @Bean
    public CreatedEventNotifier createdEventNotifier(MessagingSpanTextMapExtractor messagingSpanExtractor,
                                                     MessagingSpanTextMapInjector messagingSpanInjector) {
        CreatedEventNotifier createdEventNotifier = new CreatedEventNotifier(tracer, messagingSpanExtractor, messagingSpanInjector);
        camelContext.getManagementStrategy().addEventNotifier(createdEventNotifier);
        return createdEventNotifier;
    }

    @Bean
    public SentEventNotifier sentEventNotifier() {
        SentEventNotifier sentEventNotifier = new SentEventNotifier(tracer);
        camelContext.getManagementStrategy().addEventNotifier(sentEventNotifier);
        return sentEventNotifier;
    }

    @Bean
    public MessagingSpanTextMapExtractor messagingSpanExtractor() {
        return new HeaderBasedMessagingExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagingSpanTextMapInjector messagingSpanInjector(TraceKeys traceKeys) {
        return new HeaderBasedMessagingInjector(traceKeys);
    }

}
