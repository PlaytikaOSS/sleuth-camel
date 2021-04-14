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
import brave.propagation.Propagation;
import brave.propagation.ThreadLocalSpan;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.event.ExchangeCreatedEvent;
import org.apache.camel.impl.event.ExchangeSentEvent;
import org.apache.camel.spi.CamelEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static com.playtika.sleuth.camel.CreatedEventNotifier.EXCHANGE_EVENT_CREATED_ANNOTATION;
import static com.playtika.sleuth.camel.CreatedEventNotifier.EXCHANGE_ID_TAG_ANNOTATION;
import static com.playtika.sleuth.camel.SleuthCamelConstants.EXCHANGE_IS_TRACED_BY_BRAVE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@TestInstance(PER_CLASS)
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

    @Mock
    private Tracer tracer;

    private CreatedEventNotifier notifier;

    @BeforeEach
    public void setUp() throws Exception {
        when(tracing.propagation()).thenReturn(propagation);
        when(propagation.extractor(any(Propagation.Getter.class))).thenReturn(extractor);
        when(propagation.injector(any(Propagation.Setter.class))).thenReturn(injector);
        notifier = new CreatedEventNotifier(tracing, threadLocalSpan, tracer);
    }

    @Test
    public void shouldContinueSpan() {
        CamelEvent.ExchangeCreatedEvent event = mock(CamelEvent.ExchangeCreatedEvent.class);
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

        when(extractor.extract(message)).thenReturn(TraceContextOrSamplingFlags.EMPTY);
        when(threadLocalSpan.next(TraceContextOrSamplingFlags.EMPTY)).thenReturn(span);
        when(span.context()).thenReturn(traceContext);

        notifier.notify(event);

        verify(tracing, times(2)).propagation();
        verify(threadLocalSpan).next(Mockito.any());
        verify(tracer).currentSpan();
        verify(span).name("camel::" + endpointKet);
        verify(span).start();
        verify(span).annotate(EXCHANGE_EVENT_CREATED_ANNOTATION);
        verify(span).tag(EXCHANGE_ID_TAG_ANNOTATION, exchange.getExchangeId());
        verify(span).context();
        verify(exchange).setProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.TRUE);
        verify(injector).inject(traceContext, message);

        verifyNoMoreInteractions(tracing, threadLocalSpan, span);
    }

    @Test
    public void shouldStartNewSpan() {
        CamelEvent.ExchangeCreatedEvent event = mock(CamelEvent.ExchangeCreatedEvent.class);
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

        when(tracer.currentSpan()).thenReturn(span);
        when(extractor.extract(message)).thenReturn(TraceContextOrSamplingFlags.EMPTY);
        when(threadLocalSpan.next(TraceContextOrSamplingFlags.EMPTY)).thenReturn(span);
        when(span.context()).thenReturn(traceContext);

        notifier.notify(event);

        verify(tracing, times(2)).propagation();
        verify(tracer).currentSpan();
        verify(threadLocalSpan).next(Mockito.any());
        verify(span).name("camel::" + endpointKet);
        verify(span).start();
        verify(span).annotate(EXCHANGE_EVENT_CREATED_ANNOTATION);
        verify(span).tag(EXCHANGE_ID_TAG_ANNOTATION, exchange.getExchangeId());
        verify(span).context();
        verify(exchange).setProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.TRUE);
        verify(injector).inject(traceContext, message);

        verifyNoMoreInteractions(tracing, threadLocalSpan, span);
    }

    @Test
    public void shouldHonourRemoteSpanFromMessage() {
        CamelEvent.ExchangeCreatedEvent event = mock(CamelEvent.ExchangeCreatedEvent.class);
        Exchange exchange = mock(Exchange.class);
        Endpoint endpoint = mock(Endpoint.class);
        Message message = mock(Message.class);
        Span span = mock(Span.class);
        String someExchangeId = "some exchange Id";
        String endpointKet = "camelDirectRoute";

        when(event.getExchange()).thenReturn(exchange);
        when(exchange.getFromEndpoint()).thenReturn(endpoint);
        when(exchange.getIn()).thenReturn(message);
        when(exchange.getExchangeId()).thenReturn(someExchangeId);
        when(endpoint.getEndpointKey()).thenReturn(endpointKet);

        when(extractor.extract(message)).thenReturn(extractedContext);
        when(threadLocalSpan.next(extractedContext)).thenReturn(span);

        notifier.notify(event);

        verify(tracing, times(2)).propagation();
        verify(tracer).currentSpan();
        verify(threadLocalSpan).next(Mockito.any());
        verify(span).name("camel::" + endpointKet);
        verify(span).start();
        verify(span).annotate(EXCHANGE_EVENT_CREATED_ANNOTATION);
        verify(span).tag(EXCHANGE_ID_TAG_ANNOTATION, exchange.getExchangeId());
        verify(exchange).setProperty(EXCHANGE_IS_TRACED_BY_BRAVE, Boolean.TRUE);

        verifyNoMoreInteractions(tracing, threadLocalSpan, span);
    }

    @Test
    public void shouldBeEnabledInCaseOfCreatedEvent() {
        CamelEvent event = new ExchangeCreatedEvent(mock(Exchange.class));

        boolean result = notifier.isEnabled(event);

        assertTrue(result);
    }

    @Test
    public void shouldNotBeEnabledInCaseOfSentEvent() {
        CamelEvent event = new ExchangeSentEvent(mock(Exchange.class), null, 100);

        boolean result = notifier.isEnabled(event);

        assertFalse(result);
    }

}
