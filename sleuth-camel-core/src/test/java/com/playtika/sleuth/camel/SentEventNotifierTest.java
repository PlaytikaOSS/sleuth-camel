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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

import java.util.EventObject;

import static com.playtika.sleuth.camel.SleuthCamelConstants.FROM_CAMEL;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SentEventNotifierTest {

    @Mock
    private Tracer tracer;
    @Mock
    private ErrorParser errorParser;
    @InjectMocks
    private SentEventNotifier sentEventNotifier;

    @After
    public void tearDown() {
        verifyNoMoreInteractions(tracer, errorParser);
    }

    @Test
    public void shouldProceedRemoteSpan() {
        EventObject event = new ExchangeCompletedEvent(mock(Exchange.class));
        Span currentSpan = mock(Span.class);
        Span spanToSend = mock(Span.class);
        Span.SpanBuilder spanBuilder = mock(Span.SpanBuilder.class);

        when(tracer.isTracing()).thenReturn(true);
        when(tracer.getCurrentSpan()).thenReturn(currentSpan);
        when(currentSpan.tags()).thenReturn(singletonMap(FROM_CAMEL, "true"));
        when(currentSpan.isRemote()).thenReturn(true);
        when(currentSpan.toBuilder()).thenReturn(spanBuilder);
        when(spanBuilder.remote(false)).thenReturn(spanBuilder);
        when(spanBuilder.build()).thenReturn(spanToSend);

        sentEventNotifier.notify(event);

        verify(currentSpan).logEvent(Span.SERVER_SEND);
        verify(tracer).close(spanToSend);
        verifyNoMoreInteractions(currentSpan, spanToSend);
    }

    @Test
    public void shouldProceedLocalSpan() {
        EventObject event = new ExchangeCompletedEvent(mock(Exchange.class));
        Span currentSpan = mock(Span.class);

        when(tracer.isTracing()).thenReturn(true);
        when(tracer.getCurrentSpan()).thenReturn(currentSpan);
        when(currentSpan.tags()).thenReturn(singletonMap(FROM_CAMEL, "true"));
        when(currentSpan.isRemote()).thenReturn(false);

        sentEventNotifier.notify(event);

        verify(currentSpan).logEvent(Span.CLIENT_RECV);
        verify(tracer).close(currentSpan);
        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldProceedWithFailureMessage() {
        RuntimeException exception = new RuntimeException("exception message");
        Exchange mock = mock(Exchange.class);
        Span currentSpan = mock(Span.class);
        EventObject event = new ExchangeCompletedEvent(mock);

        when(tracer.isTracing()).thenReturn(true);
        when(tracer.getCurrentSpan()).thenReturn(currentSpan);
        when(currentSpan.tags()).thenReturn(singletonMap(FROM_CAMEL, "true"));
        when(currentSpan.isRemote()).thenReturn(false);
        when(mock.getException()).thenReturn(exception);

        sentEventNotifier.notify(event);

        verify(currentSpan).logEvent(Span.CLIENT_RECV);
        verify(tracer).close(currentSpan);
        verify(errorParser).parseErrorTags(currentSpan, exception);
        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldNotProceedIfCameFromDifferentRoute() {
        Endpoint eventEndpoint = mock(Endpoint.class);
        Endpoint exchangeEndpoint = mock(Endpoint.class);
        Exchange exchange = mock(Exchange.class);
        Span currentSpan = mock(Span.class);
        EventObject event = new ExchangeSentEvent(exchange, eventEndpoint, 0);

        when(tracer.isTracing()).thenReturn(true);
        when(tracer.getCurrentSpan()).thenReturn(currentSpan);
        when(currentSpan.tags()).thenReturn(singletonMap(FROM_CAMEL, "true"));
        when(exchange.getFromEndpoint()).thenReturn(exchangeEndpoint);
        when(eventEndpoint.getEndpointKey()).thenReturn("direct:/test1");
        when(exchangeEndpoint.getEndpointKey()).thenReturn("direct:/test2");

        sentEventNotifier.notify(event);

        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldNotProceedIfSpanNotFromCamel() {
        EventObject event = new ExchangeCompletedEvent(mock(Exchange.class));
        Span currentSpan = mock(Span.class);

        when(tracer.isTracing()).thenReturn(true);
        when(tracer.getCurrentSpan()).thenReturn(currentSpan);
        when(currentSpan.tags()).thenReturn(emptyMap());

        sentEventNotifier.notify(event);

        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldNotProceedIfNotTracing() {
        EventObject event = new ExchangeCompletedEvent(mock(Exchange.class));

        when(tracer.isTracing()).thenReturn(false);

        sentEventNotifier.notify(event);
    }

    @Test
    public void shouldBeEnabled() {
        EventObject event = new EventObject(mock(Exchange.class));

        boolean result = sentEventNotifier.isEnabled(event);

        assertTrue(result);
    }
}