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

package com.playtika.sleuth.camel.functional;

import brave.ScopedSpan;
import brave.Tracer;
import org.apache.camel.*;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.playtika.sleuth.camel.functional.TestApp.*;
import static org.apache.camel.component.mock.MockEndpoint.resetMocks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "logging.level.com.playtika.sleuth.camel=trace"
        })
public class FunctionalTest {

    private static final String SPAN_ID_HEADER_NAME = "X-B3-SpanId";
    private static final String TRACE_ID_HEADER_NAME = "X-B3-TraceId";
    private static final String SAMPLED_ID_HEADER_NAME = "X-B3-Sampled";
    private static final String PARENT_ID_HEADER_BANE = "X-B3-ParentSpanId";

    private static final Object TEST_BODY = "Some body";

    @Produce(uri = DIRECT_ROUTE_URI)
    private ProducerTemplate directRouteProducer;

    @Produce(uri = ASYNC_DIRECT_ROUTE_URI)
    private ProducerTemplate asyncDirectRouteProducer;

    @EndpointInject(uri = MOCK_DIRECT_ROUTE_TO_URI)
    private MockEndpoint directRouteMockEndpoint;

    @EndpointInject(uri = MOCK_EXCEPTION_ROUTE_TO_URI)
    private MockEndpoint exceptionRouteMockEndpoint;

    @Autowired
    private ArrayListSpanReporter spanReporter;

    @Autowired
    private Tracer tracer;
    @Autowired
    private CamelContext camelContext;

    @MockBean
    private Processor mockProcessor;

    private ScopedSpan existingSpan;

    @After
    public void tearDown() throws Exception {
        resetMocks(camelContext);
        if (existingSpan != null) {
            existingSpan.finish();
            existingSpan = null;
        }
        spanReporter.clear();

        verify(mockProcessor).process(any(Exchange.class));
        verifyNoMoreInteractions(mockProcessor);
    }

    @Test
    public void shouldSendToRouteWithoutTracing() throws Exception {
        assertSentToRouteWithoutTracing(directRouteProducer, DIRECT_ROUTE_ID);
    }

    @Test
    public void shouldSendToRouteWithoutTracingToAsyncRoute() throws Exception {
        assertSentToRouteWithoutTracing(asyncDirectRouteProducer, ASYNC_DIRECT_ROUTE_ID);
    }

    private void assertSentToRouteWithoutTracing(ProducerTemplate routeProducer, String route) throws InterruptedException {
        directRouteMockEndpoint.expectedMessageCount(1);
        directRouteMockEndpoint.expectedBodiesReceived(TEST_BODY);

        routeProducer.sendBody(TEST_BODY);

        directRouteMockEndpoint.assertIsSatisfied();

        //assert current span
        assertThat(tracer.currentSpan()).isNull();

        //assert message sent with span headers
        Map<String, Object> headers = getFirstMessageHeaders(directRouteMockEndpoint);
        assertThat(headers.size()).isEqualTo(4);
        assertThat(headers.get(SPAN_ID_HEADER_NAME)).isNotNull();
        assertThat(headers.get(TRACE_ID_HEADER_NAME)).isNotNull();
        assertThat(headers.get("X-B3-Sampled")).isEqualTo("1");

        //assert span logs
        assertAccumulatedSpans(route);
    }

    @Test
    public void shouldSendToRouteWithExistingSpan() throws Exception {
        assertSentToRouteWithExistingSpan(directRouteProducer, DIRECT_ROUTE_ID);
    }

    @Test
    public void shouldSendToRouteWithExistingSpanToAsyncRoute() throws Exception {
        assertSentToRouteWithExistingSpan(asyncDirectRouteProducer, ASYNC_DIRECT_ROUTE_ID);
    }

    private void assertSentToRouteWithExistingSpan(ProducerTemplate routeProducer, String route) throws Exception {
        existingSpan = tracer.startScopedSpan("existingSpan");

        directRouteMockEndpoint.expectedMessageCount(1);
        directRouteMockEndpoint.expectedBodiesReceived(TEST_BODY);

        routeProducer.sendBody(TEST_BODY);

        directRouteMockEndpoint.assertIsSatisfied();

        //assert current span
        assertThat(tracer.currentSpan().context()).isEqualTo(existingSpan.context());

        //assert message sent with span headers
        Map<String, Object> headers = getFirstMessageHeaders(directRouteMockEndpoint);
        assertThat(headers.size()).isEqualTo(5);
        assertThat(headers.get(SPAN_ID_HEADER_NAME)).isNotNull();
        assertThat(headers.get(TRACE_ID_HEADER_NAME)).isEqualTo(Long.toHexString(existingSpan.context().traceId()));
        assertThat(headers.get(SAMPLED_ID_HEADER_NAME)).isEqualTo("1");
        assertThat(headers.get(PARENT_ID_HEADER_BANE)).isEqualTo(Long.toHexString(existingSpan.context().spanId()));

        assertAccumulatedSpans(route);

        verify(mockProcessor).process(any(Exchange.class));
    }

    @Test
    public void shouldSendToRouteWithSpanInHeaders() throws Exception {
        assertSentToRouteWithSpanInHeaders(directRouteProducer, DIRECT_ROUTE_ID);
    }

    @Test
    public void shouldSendToRouteWithSpanInHeadersToAsyncRoute() throws Exception {
        assertSentToRouteWithSpanInHeaders(asyncDirectRouteProducer, ASYNC_DIRECT_ROUTE_ID);
    }

    private void assertSentToRouteWithSpanInHeaders(ProducerTemplate routeProducer, String route) throws Exception {
        directRouteMockEndpoint.expectedMessageCount(1);
        directRouteMockEndpoint.expectedBodiesReceived(TEST_BODY);

        String spanId = Long.toHexString(735676786785678L);
        String traceId = Long.toHexString(656565656565656L);
        String sampled = "1";

        Map<String, Object> incomingMessageHeaders = new HashMap<>();
        incomingMessageHeaders.put(SPAN_ID_HEADER_NAME, spanId);
        incomingMessageHeaders.put(TRACE_ID_HEADER_NAME, traceId);
        incomingMessageHeaders.put(SAMPLED_ID_HEADER_NAME, sampled);

        routeProducer.sendBodyAndHeaders(TEST_BODY, incomingMessageHeaders);

        directRouteMockEndpoint.assertIsSatisfied();

        //assert current span
        assertThat(tracer.currentSpan()).isNull();

        //assert message sent with span headers
        Map<String, Object> headers = getFirstMessageHeaders(directRouteMockEndpoint);
        assertThat(headers.size()).isEqualTo(5);
        assertThat((String) headers.get(TRACE_ID_HEADER_NAME)).endsWith(traceId);
        assertThat((String) headers.get(SAMPLED_ID_HEADER_NAME)).isEqualTo(sampled);
        assertThat((String) headers.get(PARENT_ID_HEADER_BANE)).endsWith(spanId);

        //assert span logs
        assertAccumulatedSpans(route);

        verify(mockProcessor).process(any(Exchange.class));
    }

    @Test
    public void shouldSendFailureSpan() throws Exception {
        assertFailureSpanSent(directRouteProducer, DIRECT_ROUTE_ID);
    }

    @Test
    public void shouldSendFailureSpanToAsyncRoute() throws Exception {
        assertFailureSpanSent(asyncDirectRouteProducer, ASYNC_DIRECT_ROUTE_ID);
    }

    private void assertFailureSpanSent(ProducerTemplate routeProducer, String routeName) throws Exception {
        String errorMessage = "Something went wrong";

        directRouteMockEndpoint.expectedMessageCount(0);
        exceptionRouteMockEndpoint.expectedBodiesReceived(1);
        exceptionRouteMockEndpoint.expectedBodiesReceived(TEST_BODY);

        doThrow(new RuntimeException(errorMessage)).when(mockProcessor).process(any(Exchange.class));

        //test
        assertThatThrownBy(() -> routeProducer.sendBody(TEST_BODY))
                .isInstanceOf(CamelExecutionException.class);

        directRouteMockEndpoint.assertIsSatisfied();
        exceptionRouteMockEndpoint.assertIsSatisfied();

        //assert current span
        assertThat(tracer.currentSpan()).isNull();

        //assert message sent with span headers
        Map<String, Object> headers = getFirstMessageHeaders(exceptionRouteMockEndpoint);
        assertThat(headers.size()).isGreaterThanOrEqualTo(4);
        assertThat(headers.get(SPAN_ID_HEADER_NAME)).isNotNull();
        assertThat(headers.get(TRACE_ID_HEADER_NAME)).isNotNull();
        assertThat(headers.get(SAMPLED_ID_HEADER_NAME)).isEqualTo("1");

        //assert span logs
        zipkin2.Span span = getSingleAccumulatedSpan();
        assertTagApplied(span, "error", errorMessage);

        verify(mockProcessor).process(any(Exchange.class));
    }

    private void assertTagApplied(zipkin2.Span span, String tag, String value) {
        String sampledErrorMessage = span.tags().get(tag);
        assertThat(sampledErrorMessage).isEqualTo(value);
    }

    private Map<String, Object> getFirstMessageHeaders(MockEndpoint endpoint) {
        Message message = endpoint.getExchanges().get(0).getIn();
        assertThat(message).isNotNull();
        return message.getHeaders();
    }

    private zipkin2.Span getSingleAccumulatedSpan() {
        List<zipkin2.Span> spans = spanReporter.getSpans();
        assertThat(spans.size()).isEqualTo(1);
        return spans.get(0);
    }

    private void assertAccumulatedSpans(String routeName) {
        List<zipkin2.Span> spans = spanReporter.getSpans();
        assertThat(spans.size()).isEqualTo(2);

        zipkin2.Span childSpan = spans.get(0);
        zipkin2.Span span = spans.get(1);

        assertSpanParameters(span, childSpan, routeName);
    }

    private void assertSpanParameters(zipkin2.Span parent, zipkin2.Span child, String routeName) {
        assertThat(child.traceId()).isEqualTo(parent.traceId());
        assertThat(child.parentId()).isEqualTo(parent.id());
        assertThat(parent.name()).isEqualTo("camel::direct://" + routeName);
    }

}
