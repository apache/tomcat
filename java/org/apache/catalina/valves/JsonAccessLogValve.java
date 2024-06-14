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
 * <p>
 * <b>Important note: the attribute names are not final.</b>
 * <p>
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
 * <li>%{xxx}a: remoteAddress-xxx</li>
 * <li>%{xxx}p: port-xxx</li>
 * <li>%{xxx}t: time-xxx</li>
 * <li>%{xxx}c: cookies</li>
 * <li>%{xxx}i: requestHeaders</li>
 * <li>%{xxx}o: responseHeaders</li>
 * <li>%{xxx}r: requestAttributes</li>
 * <li>%{xxx}s: sessionAttributes</li>
 * </ul>
 * The attribute list is based on https://github.com/fluent/fluentd/blob/master/lib/fluent/plugin/parser_apache2.rb#L72
 */
public class JsonAccessLogValve extends AccessLogValve {

    private static final Map<Character,String> PATTERNS;
    static {
        Map<Character,String> pattern2AttributeName = new HashMap<>();
        pattern2AttributeName.put(Character.valueOf('a'), "remoteAddr");
        pattern2AttributeName.put(Character.valueOf('A'), "localAddr");
        pattern2AttributeName.put(Character.valueOf('b'), "size");
        pattern2AttributeName.put(Character.valueOf('B'), "byteSentNC");
        pattern2AttributeName.put(Character.valueOf('D'), "elapsedTime");
        pattern2AttributeName.put(Character.valueOf('F'), "firstByteTime");
        pattern2AttributeName.put(Character.valueOf('h'), "host");
        pattern2AttributeName.put(Character.valueOf('H'), "protocol");
        pattern2AttributeName.put(Character.valueOf('I'), "threadName");
        pattern2AttributeName.put(Character.valueOf('l'), "logicalUserName");
        pattern2AttributeName.put(Character.valueOf('m'), "method");
        pattern2AttributeName.put(Character.valueOf('p'), "port");
        pattern2AttributeName.put(Character.valueOf('q'), "query");
        pattern2AttributeName.put(Character.valueOf('r'), "request");
        pattern2AttributeName.put(Character.valueOf('s'), "statusCode");
        pattern2AttributeName.put(Character.valueOf('S'), "sessionId");
        pattern2AttributeName.put(Character.valueOf('t'), "time");
        pattern2AttributeName.put(Character.valueOf('T'), "elapsedTimeS");
        pattern2AttributeName.put(Character.valueOf('u'), "user");
        pattern2AttributeName.put(Character.valueOf('U'), "path");
        pattern2AttributeName.put(Character.valueOf('v'), "localServerName");
        pattern2AttributeName.put(Character.valueOf('X'), "connectionStatus");
        PATTERNS = Collections.unmodifiableMap(pattern2AttributeName);
    }

    private static final Map<Character,String> SUB_OBJECT_PATTERNS;
    static {
        Map<Character,String> pattern2AttributeName = new HashMap<>();
        pattern2AttributeName.put(Character.valueOf('c'), "cookies");
        pattern2AttributeName.put(Character.valueOf('i'), "requestHeaders");
        pattern2AttributeName.put(Character.valueOf('o'), "responseHeaders");
        pattern2AttributeName.put(Character.valueOf('r'), "requestAttributes");
        pattern2AttributeName.put(Character.valueOf('s'), "sessionAttributes");
        SUB_OBJECT_PATTERNS = Collections.unmodifiableMap(pattern2AttributeName);
    }

    /**
     * write any char
     */
    protected static class CharElement implements AccessLogElement {
        private final char ch;

        public CharElement(char ch) {
            this.ch = ch;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            buf.write(ch);
        }
    }

    private boolean addSubkeyedItems(ListIterator<AccessLogElement> iterator, List<JsonWrappedElement> elements,
            String patternAttribute) {
        if (!elements.isEmpty()) {
            iterator.add(new StringElement("\"" + patternAttribute + "\": {"));
            for (JsonWrappedElement element : elements) {
                iterator.add(element);
                iterator.add(new CharElement(','));
            }
            iterator.previous();
            iterator.remove();
            iterator.add(new StringElement("},"));
            return true;
        }
        return false;
    }

    @Override
    protected AccessLogElement[] createLogElements() {
        Map<Character,List<JsonWrappedElement>> subTypeLists = new HashMap<>();
        for (Character pattern : SUB_OBJECT_PATTERNS.keySet()) {
            subTypeLists.put(pattern, new ArrayList<>());
        }
        boolean hasSub = false;
        List<AccessLogElement> logElements = new ArrayList<>(Arrays.asList(super.createLogElements()));
        ListIterator<AccessLogElement> lit = logElements.listIterator();
        lit.add(new CharElement('{'));
        while (lit.hasNext()) {
            AccessLogElement logElement = lit.next();
            // remove all other elements, like StringElements
            if (!(logElement instanceof JsonWrappedElement)) {
                lit.remove();
                continue;
            }
            // Remove items which should be written as
            // Json objects and add them later in correct order
            JsonWrappedElement wrappedLogElement = (JsonWrappedElement) logElement;
            AccessLogElement ale = wrappedLogElement.getDelegate();
            if (ale instanceof HeaderElement) {
                subTypeLists.get(Character.valueOf('i')).add(wrappedLogElement);
                lit.remove();
            } else if (ale instanceof ResponseHeaderElement) {
                subTypeLists.get(Character.valueOf('o')).add(wrappedLogElement);
                lit.remove();
            } else if (ale instanceof RequestAttributeElement) {
                subTypeLists.get(Character.valueOf('r')).add(wrappedLogElement);
                lit.remove();
            } else if (ale instanceof SessionAttributeElement) {
                subTypeLists.get(Character.valueOf('s')).add(wrappedLogElement);
                lit.remove();
            } else if (ale instanceof CookieElement) {
                subTypeLists.get(Character.valueOf('c')).add(wrappedLogElement);
                lit.remove();
            } else {
                // Keep the simple items and add separator
                lit.add(new CharElement(','));
            }
        }
        // Add back the items that are output as Json objects
        for (Character pattern : SUB_OBJECT_PATTERNS.keySet()) {
            if (addSubkeyedItems(lit, subTypeLists.get(pattern), SUB_OBJECT_PATTERNS.get(pattern))) {
                hasSub = true;
            }
        }
        // remove last comma (or possibly "},")
        lit.previous();
        lit.remove();
        // Last item was a sub object, close it
        if (hasSub) {
            lit.add(new StringElement("}}"));
        } else {
            lit.add(new CharElement('}'));
        }
        return logElements.toArray(new AccessLogElement[0]);
    }

    @Override
    protected AccessLogElement createAccessLogElement(String name, char pattern) {
        AccessLogElement ale = super.createAccessLogElement(name, pattern);
        return new JsonWrappedElement(pattern, name, true, ale);
    }

    @Override
    protected AccessLogElement createAccessLogElement(char pattern) {
        AccessLogElement ale = super.createAccessLogElement(pattern);
        return new JsonWrappedElement(pattern, true, ale);
    }

    private static class JsonWrappedElement implements AccessLogElement, CachedElement {

        private CharSequence attributeName;
        private boolean quoteValue;
        private AccessLogElement delegate;

        private CharSequence escapeJsonString(CharSequence nonEscaped) {
            return JSONFilter.escape(nonEscaped);
        }

        JsonWrappedElement(char pattern, String key, boolean quoteValue, AccessLogElement delegate) {
            this.quoteValue = quoteValue;
            this.delegate = delegate;
            String patternAttribute = PATTERNS.get(Character.valueOf(pattern));
            if (patternAttribute == null) {
                patternAttribute = "other-" + Character.toString(pattern);
            }
            if (key != null && !"".equals(key)) {
                if (SUB_OBJECT_PATTERNS.containsKey(Character.valueOf(pattern))) {
                    this.attributeName = escapeJsonString(key);
                } else {
                    this.attributeName = escapeJsonString(patternAttribute + "-" + key);
                }
            } else {
                this.attributeName = escapeJsonString(patternAttribute);
            }
        }

        JsonWrappedElement(char pattern, boolean quoteValue, AccessLogElement delegate) {
            this(pattern, null, quoteValue, delegate);
        }

        public AccessLogElement getDelegate() {
            return delegate;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            buf.append('"').append(attributeName).append('"').append(':');
            if (quoteValue) {
                buf.append('"');
            }
            delegate.addElement(buf, date, request, response, time);
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
