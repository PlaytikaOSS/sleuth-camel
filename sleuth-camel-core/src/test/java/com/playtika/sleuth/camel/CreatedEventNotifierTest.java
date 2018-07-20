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
import org.apache.camel.Message;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapExtractor;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSpanTextMapInjector;

import java.util.EventObject;

import static com.playtika.sleuth.camel.SleuthCamelConstants.FROM_CAMEL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CreatedEventNotifierTest {

    @Mock
    private Tracer tracer;
    @Mock
    private MessagingSpanTextMapExtractor spanExtractor;
    @Mock
    private MessagingSpanTextMapInjector spanInjector;

    @InjectMocks
    private CreatedEventNotifier notifier;

    @Test
    public void shouldCreateRemoteSpanFromMessage() {
        ExchangeCreatedEvent event = mock(ExchangeCreatedEvent.class);
        Exchange exchange = mock(Exchange.class);
        Endpoint endpoint = mock(Endpoint.class);
        Message message = mock(Message.class);
        Span builtSpan = mock(Span.class);

        when(event.getExchange()).thenReturn(exchange);
        when(exchange.getFromEndpoint()).thenReturn(endpoint);
        when(exchange.getIn()).thenReturn(message);
        when(spanExtractor.joinTrace(any(SpanTextMap.class))).thenReturn(builtSpan);

        notifier.notify(event);

        verify(tracer).continueSpan(builtSpan);
        verify(builtSpan).logEvent(Span.SERVER_RECV);
        verify(builtSpan).tag(FROM_CAMEL, "true");
        verify(spanInjector).inject(eq(builtSpan), any(SpanTextMap.class));
        verifyNoMoreInteractions(tracer, spanExtractor, spanInjector);
    }

    @Test
    public void shouldCreateNewSpanInCaseMessageMissingSpanData() {
        ExchangeCreatedEvent event = mock(ExchangeCreatedEvent.class);
        Exchange exchange = mock(Exchange.class);
        Endpoint endpoint = mock(Endpoint.class);
        Message message = mock(Message.class);
        Span newSpan = mock(Span.class);

        when(event.getExchange()).thenReturn(exchange);
        when(exchange.getFromEndpoint()).thenReturn(endpoint);
        when(exchange.getIn()).thenReturn(message);
        when(spanExtractor.joinTrace(any(SpanTextMap.class))).thenReturn(null);
        when(endpoint.getEndpointKey()).thenReturn("direct://someRoute");
        when(tracer.createSpan("camel::direct://someRoute")).thenReturn(newSpan);

        notifier.notify(event);

        verify(newSpan).logEvent(Span.CLIENT_SEND);
        verify(newSpan).tag(FROM_CAMEL, "true");
        verify(spanInjector).inject(eq(newSpan), any(SpanTextMap.class));
        verifyNoMoreInteractions(tracer, spanExtractor, spanInjector);
    }

    @Test
    public void shouldCreateNewSpanInCaseExtractorThrowException() {
        ExchangeCreatedEvent event = mock(ExchangeCreatedEvent.class);
        Exchange exchange = mock(Exchange.class);
        Endpoint endpoint = mock(Endpoint.class);
        Message message = mock(Message.class);
        Span newSpan = mock(Span.class);

        when(event.getExchange()).thenReturn(exchange);
        when(exchange.getFromEndpoint()).thenReturn(endpoint);
        when(exchange.getIn()).thenReturn(message);
        when(spanExtractor.joinTrace(any(SpanTextMap.class))).thenThrow(new RuntimeException("Something went wrong"));
        when(endpoint.getEndpointKey()).thenReturn("direct://someRoute");
        when(tracer.createSpan("camel::direct://someRoute")).thenReturn(newSpan);

        notifier.notify(event);

        verify(newSpan).logEvent(Span.CLIENT_SEND);
        verify(newSpan).tag(FROM_CAMEL, "true");
        verify(spanInjector).inject(eq(newSpan), any(SpanTextMap.class));
        verifyNoMoreInteractions(tracer, spanExtractor, spanInjector);
    }

    @Test
    public void shouldBeEnabledInCaseOfCreatedEvent() {
        EventObject event = new ExchangeCreatedEvent(mock(Exchange.class));

        boolean result = notifier.isEnabled(event);

        assertTrue(result);
    }

    @Test
    public void shouldNotBeEnabledInCaseOfSentEvent() {
        EventObject event = new ExchangeSentEvent(mock(Exchange.class), null, 100);

        boolean result = notifier.isEnabled(event);

        assertFalse(result);
    }

}