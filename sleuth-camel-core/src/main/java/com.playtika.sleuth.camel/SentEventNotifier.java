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
import org.apache.camel.Exchange;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import java.util.EventObject;
import java.util.Objects;

import static com.playtika.sleuth.camel.SleuthCamelConstants.FROM_CAMEL;

@Slf4j
@AllArgsConstructor
public class SentEventNotifier extends EventNotifierSupport {

    private final Tracer tracer;
    private final ErrorParser errorParser;

    @Override
    public void notify(EventObject event) {
        if (!(event instanceof ExchangeFailedEvent) && !(event instanceof ExchangeCompletedEvent) && !(event instanceof ExchangeSentEvent)) {
            return;
        }

        log.trace("Caught an event [{} - {}] - processing...", event.getClass().getSimpleName(), event);
        if (!tracer.isTracing()) {
            log.debug("Skipping event [{}] since it's not tracing...", event);
            return;
        }

        Span currentSpan = tracer.getCurrentSpan();
        if (!isCamelSpan(currentSpan)) {
            log.debug("Skipping span {}, since it's not camel one.", currentSpan);
            return;
        }

        if (!isFromSourceEndpoint(event)) {
            log.debug("Skipping span {}, since exchange came not from its source route. Event - [{}].", currentSpan, event);
            return;
        }

        if (currentSpan.isRemote()) {
            log.debug("Span {} is remote log :ss: event.", currentSpan);
            currentSpan.logEvent(Span.SERVER_SEND);
            //need to be unset as remote in order to be sent for collection
            currentSpan = currentSpan.toBuilder().remote(false).build();
        } else {
            log.debug("Span {} is not remote log :cr: event.", currentSpan);
            currentSpan.logEvent(Span.CLIENT_RECV);
        }

        logExceptionIfExists(event, currentSpan);

        tracer.close(currentSpan);
        log.debug("Span {} successfully closed.", currentSpan);
    }

    private boolean isCamelSpan(Span span) {
        return span.tags().containsKey(FROM_CAMEL);
    }

    /**
     * Handling the case when exchange goes to child route, assuming to have single span for all nested routes.
     */
    private boolean isFromSourceEndpoint(EventObject event) {
        if (!(event instanceof ExchangeSentEvent)) {
            return true;
        }
        ExchangeSentEvent exchangeSentEvent = (ExchangeSentEvent) event;
        Exchange exchange = exchangeSentEvent.getExchange();
        String exchangeEndpointKey = exchange.getFromEndpoint().getEndpointKey();
        String eventEndpointKey = exchangeSentEvent.getEndpoint().getEndpointKey();
        return Objects.equals(exchangeEndpointKey, eventEndpointKey);
    }

    private void logExceptionIfExists(EventObject event, Span span) {
        AbstractExchangeEvent abstractExchangeEvent = (AbstractExchangeEvent) event;
        Exception exception = abstractExchangeEvent.getExchange().getException();
        if (exception != null) {
            errorParser.parseErrorTags(span, exception);
        }
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return true;
    }
}

