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
import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.ThreadLocalSpan;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.EventObject;

import static com.playtika.sleuth.camel.CreatedEventNotifier.EXCHANGE_EVENT_CREATED_ANNOTATION;
import static com.playtika.sleuth.camel.CreatedEventNotifier.EXCHANGE_ID_TAG_ANNOTATION;
import static com.playtika.sleuth.camel.SleuthCamelConstants.EXCHANGE_IS_TRACED_BY_BRAVE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CreatedEventNotifierTest {

    @Mock
    private ThreadLocalSpan threadLocalSpan;
    @Mock
    private Tracing tracing;
    @Mock
    private Propagation<String> propagation;

    @Mock
    private TraceContext.Injector<Message> injector;
    @Mock
    private TraceContext.Extractor<Message> extractor;
    @Mock
    private TraceContextOrSamplingFlags extractedContext;

    private CreatedEventNotifier notifier;

    @Before
    public void setUp() {
        when(tracing.propagation()).thenReturn(propagation);
        when(propagation.extractor(any(Propagation.Getter.class))).thenReturn(extractor);
        when(propagation.injector(any(Propagation.Setter.class))).thenReturn(injector);

        notifier = new CreatedEventNotifier(tracing, threadLocalSpan);
    }

    @Test
    public void shouldCreateRemoteSpanFromMessage() {
        ExchangeCreatedEvent event = mock(ExchangeCreatedEvent.class);
        Exchange exchange = mock(Exchange.class);
        Endpoint endpoint = mock(Endpoint.class);
        Message message = mock(Message.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        String someExchangeId = "some exchange Id";
        String endpointKet = "camelDirectRoute";

        when(event.getExchange()).thenReturn(exchange);
        when(exchange.getFromEndpoint()).thenReturn(endpoint);
        when(exchange.getIn()).thenReturn(message);
        when(exchange.getExchangeId()).thenReturn(someExchangeId);
        when(endpoint.getEndpointKey()).thenReturn(endpointKet);

        when(extractor.extract(message)).thenReturn(extractedContext);
        when(threadLocalSpan.next(extractedContext)).thenReturn(span);
        when(span.context()).thenReturn(traceContext);

        notifier.notify(event);

        verify(span).name("camel::" + endpointKet);
        verify(span).start();
        verify(span).annotate(EXCHANGE_EVENT_CREATED_ANNOTATION);
        verify(span).tag(EXCHANGE_ID_TAG_ANNOTATION, exchange.getExchangeId());
        verify(exchange).setProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.TRUE);
        verify(injector).inject(traceContext, message);

        verifyNoMoreInteractions(tracing, threadLocalSpan, span);
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