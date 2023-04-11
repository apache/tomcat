/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.valves;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.util.json.JSONFilter;

/**
 * Access log valve derivative that rewrites entries as JSON.
 * <b>Important note: the attribute names are not final</b>
 * Patterns are mapped to attributes as followed:
 * <ul>
 * <li>a: remoteAddr</li>
 * <li>A: localAddr</li>
 * <li>b: size (byteSent: size)</li>
 * <li>B: byteSentNC</li>
 * <li>D: elapsedTime</li>
 * <li>F: firstByteTime</li>
 * <li>h: host</li>
 * <li>H: protocol</li>
 * <li>l: logicalUserName</li>
 * <li>m: method</li>
 * <li>p: port</li>
 * <li>q: query</li>
 * <li>r: request</li>
 * <li>s: statusCode</li>
 * <li>S: sessionId</li>
 * <li>t: time (dateTime: time)</li>
 * <li>T: elapsedTimeS</li>
 * <li>u: user</li>
 * <li>U: path (requestURI: path)</li>
 * <li>v: localServerName</li>
 * <li>I: threadName</li>
 * <li>X: connectionStatus</li>
 * </ul>
 * The attribute list is based on
 * https://github.com/fluent/fluentd/blob/master/lib/fluent/plugin/parser_apache2.rb#L72
 */
public class JsonAccessLogValve extends AccessLogValve {

    private static final Map<Character, String> PATTERNS;
    static {
        // FIXME: finalize attribute names
        Map<Character, String> pattern2AttributeName = new HashMap<>();
        pattern2AttributeName.put(Character.valueOf('a'), "remoteAddr");
        pattern2AttributeName.put(Character.valueOf('A'), "localAddr");
        pattern2AttributeName.put(Character.valueOf('b'), "size"); /* byteSent -> size */
        pattern2AttributeName.put(Character.valueOf('B'), "byteSentNC");
        pattern2AttributeName.put(Character.valueOf('D'), "elapsedTime");
        pattern2AttributeName.put(Character.valueOf('F'), "firstByteTime");
        pattern2AttributeName.put(Character.valueOf('h'), "host");
        pattern2AttributeName.put(Character.valueOf('H'), "protocol");
        pattern2AttributeName.put(Character.valueOf('l'), "logicalUserName");
        pattern2AttributeName.put(Character.valueOf('m'), "method");
        pattern2AttributeName.put(Character.valueOf('p'), "port");
        pattern2AttributeName.put(Character.valueOf('q'), "query");
        pattern2AttributeName.put(Character.valueOf('r'), "request");
        pattern2AttributeName.put(Character.valueOf('s'), "statusCode");
        pattern2AttributeName.put(Character.valueOf('S'), "sessionId");
        pattern2AttributeName.put(Character.valueOf('t'), "time"); /* dateTime -> time */
        pattern2AttributeName.put(Character.valueOf('T'), "elapsedTimeS");
        pattern2AttributeName.put(Character.valueOf('u'), "user");
        pattern2AttributeName.put(Character.valueOf('U'), "path"); /* requestURI -> path */
        pattern2AttributeName.put(Character.valueOf('v'), "localServerName");
        pattern2AttributeName.put(Character.valueOf('I'), "threadName");
        pattern2AttributeName.put(Character.valueOf('X'), "connectionStatus");
        PATTERNS = Collections.unmodifiableMap(pattern2AttributeName);
    }

    @Override
    protected AccessLogElement[] createLogElements() {
        List<AccessLogElement> logElements = new ArrayList<>(Arrays.asList(super.createLogElements()));
        ListIterator<AccessLogElement> lit = logElements.listIterator();
        lit.add((buf, date, req, resp, time) -> buf.write('{'));
        while (lit.hasNext()) {
            AccessLogElement logElement = lit.next();
            // remove all other elements, like StringElements
            if (!(logElement instanceof JsonWrappedElement)) {
                lit.remove();
                continue;
            }
            lit.add((buf, date, req, resp, time) -> buf.write(','));
        }
        // remove last comma again
        lit.previous();
        lit.remove();
        lit.add((buf, date, req, resp, time) -> buf.write('}'));
        return logElements.toArray(new AccessLogElement[logElements.size()]);
    }

    @Override
    protected AccessLogElement createAccessLogElement(char pattern) {
        AccessLogElement ale = super.createAccessLogElement(pattern);
        String attributeName = PATTERNS.get(Character.valueOf(pattern));
        if (attributeName == null) {
            attributeName = "other-" + new String(JSONFilter.escape(pattern));
        }
        return new JsonWrappedElement(attributeName, true, ale);
    }

    /**
     * JSON string escaping writer
     */
    private static class JsonCharArrayWriter extends CharArrayWriter {

        JsonCharArrayWriter(int i) {
            super(i);
        }

        @Override
        public void write(int c) {
            try {
                super.write(JSONFilter.escape((char) c));
            } catch (IOException e) {
                // ignore
            }
        }

        @Override
        public void write(char[] c, int off, int len) {
            try {
                super.write(JSONFilter.escape(new String(c, off, len)));
            } catch (IOException e) {
                // ignore
            }
        }

        @Override
        public void write(String str, int off, int len) {
            CharSequence escaped = JSONFilter.escape(str, off, len);
            super.write(escaped.toString(), 0, escaped.length());
        }
    }

    private static class JsonWrappedElement implements AccessLogElement, CachedElement {

        private CharSequence attributeName;
        private boolean quoteValue;
        private AccessLogElement delegate;

        private CharSequence escapeJsonString(CharSequence nonEscaped) {
            return JSONFilter.escape(nonEscaped);
        }

        JsonWrappedElement(String attributeName, boolean quoteValue, AccessLogElement delegate) {
            this.attributeName = escapeJsonString(attributeName);
            this.quoteValue = quoteValue;
            this.delegate = delegate;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            buf.append('"').append(attributeName).append('"').append(':');
            if (quoteValue) {
                buf.append('"');
            }
            CharArrayWriter valueWriter = new JsonCharArrayWriter(8);
            try {
                delegate.addElement(valueWriter, date, request, response, time);
                valueWriter.writeTo(buf);
            } catch (IOException e) {
                // ignore
            }
            if (quoteValue) {
                buf.append('"');
            }
        }

        @Override
        public void cache(Request request) {
            if (delegate instanceof CachedElement) {
                ((CachedElement) delegate).cache(request);
            }
        }
    }

}
