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

import org.apache.camel.*;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessageHeaders;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.playtika.sleuth.camel.functional.TestApp.DIRECT_ROUTE_URI;
import static com.playtika.sleuth.camel.functional.TestApp.MOCK_DIRECT_ROUTE_TO_URI;
import static java.util.Comparator.comparingLong;
import static org.apache.camel.component.mock.MockEndpoint.resetMocks;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "logging.level.com.playtika.sleuth.camel=trace"
        })
public class FunctionalTest {

    private static final Object TEST_BODY = "Some body";

    @Produce(uri = DIRECT_ROUTE_URI)
    private ProducerTemplate directRouteProducer;

    @EndpointInject(uri = MOCK_DIRECT_ROUTE_TO_URI)
    private MockEndpoint directRouteMockEndpoint;

    @Autowired
    private ArrayListSpanAccumulator spanAccumulator;
    @Autowired
    private Tracer tracer;
    @Autowired
    private CamelContext camelContext;

    private Span existingSpan;

    @After
    public void close() {
        resetMocks(camelContext);
        tracer.close(existingSpan);
        existingSpan = null;
        spanAccumulator.getSpans().clear();
    }

    @Test
    public void shouldSendToRouteWithoutTracing() throws Exception {
        directRouteMockEndpoint.expectedMessageCount(1);
        directRouteMockEndpoint.expectedBodiesReceived(TEST_BODY);

        directRouteProducer.sendBody(TEST_BODY);

        directRouteMockEndpoint.assertIsSatisfied();

        //assert current span
        assertThat(tracer.isTracing()).isFalse();

        //assert message sent with span headers
        Map<String, Object> headers = getFirstMessageHeaders();
        assertThat(headers.size()).isEqualTo(5);
        assertThat(headers.get(TraceMessageHeaders.SPAN_ID_NAME)).isNotNull();
        assertThat(headers.get(TraceMessageHeaders.TRACE_ID_NAME)).isNotNull();
        assertThat(headers.get(TraceMessageHeaders.SAMPLED_NAME)).isEqualTo("1");
        assertThat(headers.get(TraceMessageHeaders.SPAN_NAME_NAME)).isEqualTo("camel::direct://directRoute");

        //assert span logs
        assertAccumulatedSpansAndLogsForTestingSpan(Span.CLIENT_SEND, Span.CLIENT_RECV);
    }

    @Test
    public void shouldSendToRouteWithExistingSpan() throws Exception {
        existingSpan = tracer.createSpan("existingSpan");

        directRouteMockEndpoint.expectedMessageCount(1);
        directRouteMockEndpoint.expectedBodiesReceived(TEST_BODY);

        directRouteProducer.sendBody(TEST_BODY);

        directRouteMockEndpoint.assertIsSatisfied();

        //assert current span
        assertThat(tracer.isTracing()).isTrue();
        assertThat(tracer.getCurrentSpan()).isEqualTo(existingSpan);

        //assert message sent with span headers
        Map<String, Object> headers = getFirstMessageHeaders();
        assertThat(headers.size()).isEqualTo(6);
        assertThat(headers.get(TraceMessageHeaders.SPAN_ID_NAME)).isNotNull();
        assertThat(headers.get(TraceMessageHeaders.TRACE_ID_NAME)).isEqualTo(existingSpan.traceIdString());
        assertThat(headers.get(TraceMessageHeaders.PARENT_ID_NAME)).isEqualTo(Span.idToHex(existingSpan.getSpanId()));
        assertThat(headers.get(TraceMessageHeaders.SAMPLED_NAME)).isEqualTo("1");
        assertThat(headers.get(TraceMessageHeaders.SPAN_NAME_NAME)).isEqualTo("camel::direct://directRoute");

        //assert span logs
        assertAccumulatedSpansAndLogsForTestingSpan(Span.CLIENT_SEND, Span.CLIENT_RECV);
    }

    @Test
    public void shouldSendToRouteWithSpanInHeaders() throws Exception {
        directRouteMockEndpoint.expectedMessageCount(1);
        directRouteMockEndpoint.expectedBodiesReceived(TEST_BODY);

        String spanId = Span.idToHex(545454545);
        String traceId = Span.idToHex(67667676);
        String sampled = "1";
        String spanName = "someExternalService";

        Map<String, Object> incomingMessageHeaders = new HashMap<>();
        incomingMessageHeaders.put(TraceMessageHeaders.SPAN_ID_NAME, spanId);
        incomingMessageHeaders.put(TraceMessageHeaders.TRACE_ID_NAME, traceId);
        incomingMessageHeaders.put(TraceMessageHeaders.SAMPLED_NAME, sampled);
        incomingMessageHeaders.put(TraceMessageHeaders.SPAN_NAME_NAME, spanName);

        directRouteProducer.sendBodyAndHeaders(TEST_BODY, incomingMessageHeaders);

        directRouteMockEndpoint.assertIsSatisfied();

        //assert current span
        assertThat(tracer.isTracing()).isFalse();

        //assert message sent with span headers
        Map<String, Object> headers = getFirstMessageHeaders();
        assertThat(headers.size()).isEqualTo(5);
        assertThat(headers.get(TraceMessageHeaders.SPAN_ID_NAME)).isEqualTo(spanId);
        assertThat(headers.get(TraceMessageHeaders.TRACE_ID_NAME)).isEqualTo(traceId);
        assertThat(headers.get(TraceMessageHeaders.SAMPLED_NAME)).isEqualTo(sampled);
        assertThat(headers.get(TraceMessageHeaders.SPAN_NAME_NAME)).isEqualTo(spanName);

        //assert span logs
        assertAccumulatedSpansAndLogsForTestingSpan(Span.SERVER_RECV, Span.SERVER_SEND);
    }

    private Map<String, Object> getFirstMessageHeaders() {
        Message message = directRouteMockEndpoint.getExchanges().get(0).getIn();
        assertThat(message).isNotNull();
        return message.getHeaders();
    }

    private void assertAccumulatedSpansAndLogsForTestingSpan(String... events) {
        List<Span> spans = spanAccumulator.getSpans();
        assertThat(spans.size()).isEqualTo(2);

        Span childSpan = spans.get(0);
        Span span = spans.get(1);

        assertChildSpan(span, childSpan);
        assertLogsOrdered(span, events);
    }

    private void assertLogsOrdered(Span span, String[] events) {
        assertThat(span.logs().size()).isEqualTo(events.length);

        List<Log> logs = new ArrayList<>(span.logs());
        logs.sort(comparingLong(Log::getTimestamp));

        for (int i = 0; i < events.length; i++) {
            assertThat(logs.get(i).getEvent()).isEqualTo(events[i]);
        }
    }

    private void assertChildSpan(Span parent, Span child) {
        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(child.getParents().get(0)).isEqualTo(parent.getSpanId());
    }

}
