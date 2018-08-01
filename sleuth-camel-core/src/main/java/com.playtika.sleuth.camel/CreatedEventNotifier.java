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
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapExtractor;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapInjector;
import org.springframework.cloud.sleuth.util.SpanNameUtil;

import java.util.EventObject;

import static com.playtika.sleuth.camel.SleuthCamelConstants.FROM_CAMEL;

@Slf4j
@AllArgsConstructor
public class CreatedEventNotifier extends EventNotifierSupport {

    private static final String MESSAGE_COMPONENT = "camel";

    private final Tracer tracer;
    private final MessagingSpanTextMapExtractor spanExtractor;
    private final MessagingSpanTextMapInjector spanInjector;

    @Override
    public void notify(EventObject event) {
        log.trace("Caught an event [{} - {}] - processing...", event.getClass().getSimpleName(), event);
        ExchangeCreatedEvent exchangeCreatedEvent = (ExchangeCreatedEvent) event;
        Exchange exchange = exchangeCreatedEvent.getExchange();
        Endpoint endpoint = exchange.getFromEndpoint();
        CamelMessageTextMap carrier = new CamelMessageTextMap(exchange.getIn());

        Span span = getSpan(carrier, endpoint);
        span.tag(FROM_CAMEL, "true");

        spanInjector.inject(span, carrier);
    }

    private Span getSpan(CamelMessageTextMap carrier, Endpoint endpoint) {
        log.trace("Getting span for [{}] from endpoint [{}]...", carrier, endpoint);
        Span spanFromMessage = buildSpan(carrier);
        if (spanFromMessage != null) {
            log.debug("Built span from message - {} from endpoint {}. Assuming it is remote one. Continuing it...",
                    spanFromMessage, endpoint);
            tracer.continueSpan(spanFromMessage);
            spanFromMessage.logEvent(Span.SERVER_RECV);
            return spanFromMessage;
        } else {
            String name = getSpanName(endpoint);
            Span newSpan = tracer.createSpan(name);
            newSpan.logEvent(Span.CLIENT_SEND);
            log.debug("Message doesn't contain span data, new one was created - {} for endpoint.", newSpan, endpoint);
            return newSpan;
        }
    }

    private String getSpanName(Endpoint endpoint) {
        return SpanNameUtil.shorten(MESSAGE_COMPONENT + "::" + endpoint.getEndpointKey());
    }

    private Span buildSpan(SpanTextMap carrier) {
        try {
            return this.spanExtractor.joinTrace(carrier);
        } catch (Exception e) {
            log.error("Exception occurred while trying to extract span from carrier.", e);
            return null;
        }
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return event instanceof ExchangeCreatedEvent;
    }
}
