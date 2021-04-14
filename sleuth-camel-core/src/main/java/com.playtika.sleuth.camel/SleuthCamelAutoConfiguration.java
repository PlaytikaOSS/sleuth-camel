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

import brave.Tracer;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import lombok.AllArgsConstructor;
import org.apache.camel.CamelContext;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AllArgsConstructor
@Configuration
@ConditionalOnBean(Tracer.class)
@ConditionalOnClass({CamelContext.class})
@AutoConfigureAfter({BraveAutoConfiguration.class})
@ConditionalOnProperty(value = "spring.sleuth.camel.enabled", matchIfMissing = true)
public class SleuthCamelAutoConfiguration {

    private final CamelContext camelContext;
    private final Tracer tracer;

    @Bean
    @ConditionalOnMissingBean
    public CreatedEventNotifier createdEventNotifier(Tracing tracing, ThreadLocalSpan threadLocalSpan) {
        CreatedEventNotifier createdEventNotifier = new CreatedEventNotifier(tracing, threadLocalSpan, tracer);
        camelContext.getManagementStrategy().addEventNotifier(createdEventNotifier);
        return createdEventNotifier;
    }

    @Bean
    @ConditionalOnMissingBean
    public SentEventNotifier sentEventNotifier(ThreadLocalSpan threadLocalSpan) {
        SentEventNotifier sentEventNotifier = new SentEventNotifier(tracer, threadLocalSpan);
        camelContext.getManagementStrategy().addEventNotifier(sentEventNotifier);
        return sentEventNotifier;
    }

    @Bean
    public ThreadLocalSpan threadLocalSpan() {
        return ThreadLocalSpan.create(this.tracer);
    }
}
