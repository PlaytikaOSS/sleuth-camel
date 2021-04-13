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
import brave.Tags;
import brave.Tracer;
import brave.propagation.ThreadLocalSpan;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.impl.event.AbstractExchangeEvent;
import org.apache.camel.impl.event.ExchangeSentEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;

import java.util.Objects;

import static com.playtika.sleuth.camel.SleuthCamelConstants.EXCHANGE_IS_TRACED_BY_BRAVE;

@Slf4j
@AllArgsConstructor
public class SentEventNotifier extends EventNotifierSupport {

    public static final String EXCHANGE_EVENT_SENT_ANNOTATION = "camel-exchange-event-sent";

    private final Tracer tracer;
    private final ThreadLocalSpan threadLocalSpan;

    @Override
    public void notify(CamelEvent event) {
        if (!(event instanceof CamelEvent.ExchangeFailedEvent) && !(event instanceof CamelEvent.ExchangeCompletedEvent) && !(event instanceof CamelEvent.ExchangeSentEvent)) {
            return;
        }

        log.trace("Caught an event [{} - {}] - processing...", event.getClass().getSimpleName(), event);
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            log.debug("Skipping event [{}] since it's not tracing...", event);
            return;
        }

        Exchange exchange = ((AbstractExchangeEvent) event).getExchange();
        if (!isCamelSpan(exchange)) {
            log.debug("Skipping span {}, since it's not camel one.", currentSpan);
            return;
        }

        if (!isFromSourceEndpoint(event)) {
            log.debug("Skipping span {}, since exchange came not from its source route. Event - [{}].", currentSpan, event);
            return;
        }

        exchange.removeProperty(EXCHANGE_IS_TRACED_BY_BRAVE);

        Span spanToFinish = threadLocalSpan.remove();
        logExceptionIfExists(event, spanToFinish);
        spanToFinish.annotate(EXCHANGE_EVENT_SENT_ANNOTATION);
        spanToFinish.finish();
        log.debug("Span {} successfully closed.", spanToFinish);
    }

    private boolean isCamelSpan(Exchange exchange) {
        Boolean isTracing = exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class);
        return isTracing != null && isTracing;
    }

    /**
     * Handling the case when exchange goes to child route, assuming to have single span for all nested routes.
     */
    private boolean isFromSourceEndpoint(CamelEvent event) {
        if (!(event instanceof ExchangeSentEvent)) {
            return true;
        }
        ExchangeSentEvent exchangeSentEvent = (ExchangeSentEvent) event;
        Exchange exchange = exchangeSentEvent.getExchange();
        String exchangeEndpointKey = exchange.getFromEndpoint().getEndpointKey();
        String eventEndpointKey = exchangeSentEvent.getEndpoint().getEndpointKey();
        return Objects.equals(exchangeEndpointKey, eventEndpointKey);
    }

    private void logExceptionIfExists(CamelEvent event, Span span) {
        AbstractExchangeEvent abstractExchangeEvent = (AbstractExchangeEvent) event;
        Exception exception = abstractExchangeEvent.getExchange().getException();
        if (exception != null) {
            Tags.ERROR.tag(exception, span);
        }
    }
}

