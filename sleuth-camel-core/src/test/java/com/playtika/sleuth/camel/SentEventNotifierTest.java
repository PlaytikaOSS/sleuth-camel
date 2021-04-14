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
import brave.propagation.ThreadLocalSpan;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.event.ExchangeCompletedEvent;
import org.apache.camel.impl.event.ExchangeSentEvent;
import org.apache.camel.spi.CamelEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import static com.playtika.sleuth.camel.SentEventNotifier.EXCHANGE_EVENT_SENT_ANNOTATION;
import static com.playtika.sleuth.camel.SleuthCamelConstants.EXCHANGE_IS_TRACED_BY_BRAVE;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.STRICT_STUBS;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = STRICT_STUBS)
@TestInstance(PER_METHOD)
public class SentEventNotifierTest {

    @Mock()
    private Tracer tracer;
    @Mock
    private ThreadLocalSpan threadLocalSpan;
    @InjectMocks
    private SentEventNotifier sentEventNotifier;

    @AfterEach
    public void tearDown() {
        verifyNoMoreInteractions(tracer);
    }

    @Test
    public void shouldProceedRemoteSpan() {
        Exchange exchange = mock(Exchange.class);
        CamelEvent event = new ExchangeCompletedEvent(exchange);
        Span currentSpan = mock(Span.class);
        Span spanToSend = mock(Span.class);

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(threadLocalSpan.remove()).thenReturn(spanToSend);
        when(exchange.getException()).thenReturn(null);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.TRUE);

        sentEventNotifier.notify(event);

        verify(tracer).currentSpan();
        verify(exchange).removeProperty(EXCHANGE_IS_TRACED_BY_BRAVE);
        verify(spanToSend).annotate(EXCHANGE_EVENT_SENT_ANNOTATION);
        verify(spanToSend).finish();
        verifyNoMoreInteractions(currentSpan, spanToSend);
    }

    @Test
    public void shouldProceedWithFailureMessage() {
        Exchange exchange = mock(Exchange.class);
        CamelEvent event = new ExchangeCompletedEvent(exchange);
        Span currentSpan = mock(Span.class);
        Span spanToSend = mock(Span.class);
        RuntimeException exception = new RuntimeException("some error");

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(threadLocalSpan.remove()).thenReturn(spanToSend);
        when(exchange.getException()).thenReturn(exception);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.TRUE);

        sentEventNotifier.notify(event);

        verify(tracer).currentSpan();
        verify(exchange).removeProperty(EXCHANGE_IS_TRACED_BY_BRAVE);
        verify(spanToSend).annotate(EXCHANGE_EVENT_SENT_ANNOTATION);
        verify(spanToSend).finish();
        verify(spanToSend).tag(Mockito.any(), Mockito.any());
        verify(spanToSend).annotate(Mockito.any());
        verify(spanToSend).finish();
        verify(spanToSend).isNoop();
        verify(spanToSend).context();
        verifyNoMoreInteractions(currentSpan, spanToSend);
    }

    @Test
    public void shouldNotProceedIfCameFromDifferentRoute() {
        Endpoint eventEndpoint = mock(Endpoint.class);
        Endpoint exchangeEndpoint = mock(Endpoint.class);
        Exchange exchange = mock(Exchange.class);
        Span currentSpan = mock(Span.class);
        CamelEvent event = new ExchangeSentEvent(exchange, eventEndpoint, 0);

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.TRUE);
        when(exchange.getFromEndpoint()).thenReturn(exchangeEndpoint);
        when(eventEndpoint.getEndpointKey()).thenReturn("direct:/test1");
        when(exchangeEndpoint.getEndpointKey()).thenReturn("direct:/test2");

        sentEventNotifier.notify(event);

        verify(tracer).currentSpan();
        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldNotProceedIfSpanNotFromCamel() {
        Exchange exchange = mock(Exchange.class);
        CamelEvent event = new ExchangeCompletedEvent(exchange);
        Span currentSpan = mock(Span.class);

        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(exchange.getProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.class)).thenReturn(Boolean.FALSE);

        sentEventNotifier.notify(event);

        verify(tracer).currentSpan();
        verifyNoMoreInteractions(currentSpan);
    }

    @Test
    public void shouldNotProceedIfNotTracing() {
        CamelEvent event = new ExchangeCompletedEvent(mock(Exchange.class));

        when(tracer.currentSpan()).thenReturn(null);

        sentEventNotifier.notify(event);

        verify(tracer).currentSpan();
    }

}
