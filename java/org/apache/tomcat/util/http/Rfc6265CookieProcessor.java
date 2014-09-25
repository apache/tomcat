/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.parser.Cookie;

public class Rfc6265CookieProcessor implements CookieProcessor {

    private static final Log log = LogFactory.getLog(Rfc6265CookieProcessor.class);

    @Override
    public Charset getCharset() {
        return StandardCharsets.UTF_8;
    }


    @Override
    public void parseCookieHeader(MimeHeaders headers,
            ServerCookies serverCookies) {

        if (headers == null) {
            // nothing to process
            return;
        }

        // process each "cookie" header
        int pos = headers.findHeader("Cookie", 0);
        while (pos >= 0) {
            MessageBytes cookieValue = headers.getValue(pos);

            if (cookieValue != null && !cookieValue.isNull() ) {
                if (cookieValue.getType() != MessageBytes.T_BYTES ) {
                    Exception e = new Exception();
                    log.warn("Cookies: Parsing cookie as String. Expected bytes.", e);
                    cookieValue.toBytes();
                }
                if (log.isDebugEnabled()) {
                    log.debug("Cookies: Parsing b[]: " + cookieValue.toString());
                }
                ByteChunk bc = cookieValue.getByteChunk();

                Cookie.parseCookie(bc.getBytes(), bc.getOffset(), bc.getLength(),
                        serverCookies);
            }

            // search from the next position
            pos = headers.findHeader("Cookie", ++pos);
        }
    }
}
