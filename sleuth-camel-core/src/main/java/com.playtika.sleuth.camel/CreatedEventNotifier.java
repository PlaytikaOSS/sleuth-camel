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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;

import static com.playtika.sleuth.camel.SleuthCamelConstants.EXCHANGE_IS_TRACED_BY_BRAVE;

@Slf4j
public class CreatedEventNotifier extends EventNotifierSupport {

    static final String EXCHANGE_EVENT_CREATED_ANNOTATION = "camel-exchange-event-created";
    static final String EXCHANGE_ID_TAG_ANNOTATION = "camel-exchange-id";
    private static final String MESSAGE_COMPONENT = "camel";

    private final ThreadLocalSpan threadLocalSpan;

    private final TraceContext.Injector<Message> injector;
    private final TraceContext.Extractor<Message> extractor;
    private final Tracer tracer;

    public CreatedEventNotifier(Tracing tracing, ThreadLocalSpan threadLocalSpan, Tracer tracer) {
        this.threadLocalSpan = threadLocalSpan;
        this.tracer = tracer;
        this.injector = tracing.propagation().injector(Message::setHeader);
        this.extractor = tracing.propagation().extractor((carrier, key) -> carrier.getHeader(key, String.class));
    }

    @Override
    public void notify(CamelEvent event) {
        log.trace("Caught an event [{} - {}] - processing...", event.getClass().getSimpleName(), event);
        CamelEvent.ExchangeCreatedEvent exchangeCreatedEvent = (CamelEvent.ExchangeCreatedEvent) event;
        Exchange exchange = exchangeCreatedEvent.getExchange();
        Endpoint endpoint = exchange.getFromEndpoint();
        Message message = exchange.getIn();
        TraceContextOrSamplingFlags extractedContext = extractor.extract(message);
        boolean isExternalContext = TraceContextOrSamplingFlags.EMPTY != extractedContext;
        Span possiblyExistingSpan = tracer.currentSpan();

        Span span = threadLocalSpan.next(extractedContext);
        String spanName = getSpanName(endpoint);
        span.name(spanName);
        span.start();

        span.annotate(EXCHANGE_EVENT_CREATED_ANNOTATION);
        span.tag(EXCHANGE_ID_TAG_ANNOTATION, exchange.getExchangeId());

        exchange.setProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.TRUE);

        // If an external tracing context could be extracted from the message, there is no
        // need to set a different one.
        if (!isExternalContext) {
            if (null != possiblyExistingSpan) {
                // A span already existed prior to being called, so put that span context into the message
                injector.inject(possiblyExistingSpan.context(), message);
            } else {
                // The span was created in this notifier, so use it.
                injector.inject(span.context(), message);
            }
        }
        log.debug("Created/continued span [{}]", span);
    }

    private String getSpanName(Endpoint endpoint) {
        return SpanNameUtil.shorten(MESSAGE_COMPONENT + "::" + endpoint.getEndpointKey());
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof CamelEvent.ExchangeCreatedEvent;
    }

}
