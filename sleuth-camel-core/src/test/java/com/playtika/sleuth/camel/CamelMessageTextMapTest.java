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

import org.apache.camel.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CamelMessageTextMapTest {

    @Mock
    private Message delegate;

    @InjectMocks
    private CamelMessageTextMap camelMessageTextMap;

    @Test
    public void shouldReturnHeadersIterator() {
        String oneKey = "oneKey";
        Map<String, Object> headers = Collections.singletonMap(oneKey, 1);

        when(delegate.getHeaders()).thenReturn(headers);

        Iterator<Map.Entry<String, String>> iterator = camelMessageTextMap.iterator();
        Map<String, String> resultMap = convertToMap(iterator);

        assertEquals(headers.size(), resultMap.size());
        assertEquals("1", resultMap.get(oneKey));
    }

    @Test
    public void shouldPutHeader() {
        String key = "key";
        String value = "value";

        camelMessageTextMap.put(key, value);

        verify(delegate).setHeader(key, value);
    }

    @Test
    public void shouldNotPutEmptyHeader() {
        String key = "key";
        String value = "";

        camelMessageTextMap.put(key, value);

        verifyZeroInteractions(delegate);
    }

    private Map<String, String> convertToMap(Iterator<Map.Entry<String, String>> iterator) {
        Iterable<Map.Entry<String, String>> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}