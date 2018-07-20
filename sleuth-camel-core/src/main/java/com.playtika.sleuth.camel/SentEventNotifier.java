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
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import java.util.EventObject;

import static com.playtika.sleuth.camel.SleuthCamelConstants.FROM_CAMEL;

@Slf4j
@AllArgsConstructor
public class SentEventNotifier extends EventNotifierSupport {

    private final Tracer tracer;

    @Override
    public void notify(EventObject event) {
        log.trace("Caught ExchangeCreatedEvent, processing...");
        if (!tracer.isTracing()) {
            log.debug("Skipping event sent event since it's not tracing...");
            return;
        }

        Span currentSpan = tracer.getCurrentSpan();
        if (!currentSpan.tags().containsKey(FROM_CAMEL)) {
            log.debug("Skipping span {}, since it's not camel one.", currentSpan);
            return;
        }

        if (currentSpan.isRemote()) {
            log.debug("Span {} is remote log :ss: event.", currentSpan);
            currentSpan.logEvent(Span.SERVER_SEND);
            //need to be unset as remove in order to be sent for collection
            currentSpan = currentSpan.toBuilder().remote(false).build();
        } else {
            log.debug("Span {} is not remote log :cr: event.", currentSpan);
            currentSpan.logEvent(Span.CLIENT_RECV);
        }

        tracer.close(currentSpan);
        log.debug("Span {} successfully closed.", currentSpan);
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return event instanceof ExchangeSentEvent || event instanceof ExchangeCompletedEvent;
    }
}

