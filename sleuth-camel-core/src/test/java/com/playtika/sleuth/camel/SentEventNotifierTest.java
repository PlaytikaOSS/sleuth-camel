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

import brave.ErrorParser;
import brave.Span;
import brave.Tracer;
import brave.propagation.ThreadLocalSpan;
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

import java.util.EventObject;

import static com.playtika.sleuth.camel.SentEventNotifier.EXCHANGE_EVENT_SENT_ANNOTATION;
import static com.playtika.sleuth.camel.SleuthCamelConstants.EXCHANGE_IS_TRACED_BY_BRAVE;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SentEventNotifierTest {

    @Mock
    private Tracer tracer;
    @Mock
    private ErrorParser errorParser;
    @Mock
    private ThreadLocalSpan threadLocalSpan;

    @InjectMocks
    private SentEventNotifier sentEventNotifier;

    @After
    public void tearDown() {
        verifyNoMoreInteractions(tracer, errorParser);
    }

    @Test
    public void shouldProceedRemoteSpan() {
        Exchange exchange = mock(Exchange.class);
        EventObject event = new ExchangeCompletedEvent(exchange);
        Span currentSpan = mock(Span.class);
        Span spanToSend = mock(Span.class);

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(threadLocalSpan.remove()).thenReturn(spanToSend);
        when(exchange.getException()).thenReturn(null);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.TRUE);

        sentEventNotifier.notify(event);

        verify(exchange).removeProperty(EXCHANGE_IS_TRACED_BY_BRAVE);
        verify(spanToSend).annotate(EXCHANGE_EVENT_SENT_ANNOTATION);
        verify(spanToSend).finish();
        verifyNoMoreInteractions(currentSpan, spanToSend);
    }

    @Test
    public void shouldProceedWithFailureMessage() {
        Exchange exchange = mock(Exchange.class);
        EventObject event = new ExchangeCompletedEvent(exchange);
        Span currentSpan = mock(Span.class);
        Span spanToSend = mock(Span.class);
        RuntimeException exception = new RuntimeException("some error");

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(threadLocalSpan.remove()).thenReturn(spanToSend);
        when(exchange.getException()).thenReturn(exception);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.TRUE);

        sentEventNotifier.notify(event);

        verify(exchange).removeProperty(EXCHANGE_IS_TRACED_BY_BRAVE);
        verify(errorParser).error(exception, spanToSend);
        verify(spanToSend).annotate(EXCHANGE_EVENT_SENT_ANNOTATION);
        verify(spanToSend).finish();
        verifyNoMoreInteractions(currentSpan, spanToSend);
    }

    @Test
    public void shouldNotProceedIfCameFromDifferentRoute() {
        Endpoint eventEndpoint = mock(Endpoint.class);
        Endpoint exchangeEndpoint = mock(Endpoint.class);
        Exchange exchange = mock(Exchange.class);
        Span currentSpan = mock(Span.class);
        EventObject event = new ExchangeSentEvent(exchange, eventEndpoint, 0);

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.TRUE);
        when(exchange.getFromEndpoint()).thenReturn(exchangeEndpoint);
        when(eventEndpoint.getEndpointKey()).thenReturn("direct:/test1");
        when(exchangeEndpoint.getEndpointKey()).thenReturn("direct:/test2");

        sentEventNotifier.notify(event);

        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldNotProceedIfSpanNotFromCamel() {
        Exchange exchange = mock(Exchange.class);
        EventObject event = new ExchangeCompletedEvent(exchange);
        Span currentSpan = mock(Span.class);

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.FALSE);

        sentEventNotifier.notify(event);

        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldNotProceedIfNotTracing() {
        EventObject event = new ExchangeCompletedEvent(mock(Exchange.class));

        when(tracer.currentSpan()).thenReturn(null);

        sentEventNotifier.notify(event);
    }

    @Test
    public void shouldBeEnabled() {
        EventObject event = new EventObject(mock(Exchange.class));

        boolean result = sentEventNotifier.isEnabled(event);

        assertTrue(result);
    }
}