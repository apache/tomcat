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
package org.apache.catalina.util;

import org.apache.tomcat.util.buf.HexUtils;

public class StandardSessionIdGenerator extends SessionIdGeneratorBase {

    @Override
    public String generateSessionId(String route) {

        byte random[] = new byte[16];
        int sessionIdLength = getSessionIdLength();

        // Render the result as a String of hexadecimal digits
        // Start with enough space for sessionIdLength and medium route size
        StringBuilder buffer = new StringBuilder(2 * sessionIdLength + 20);

        int resultLenBytes = 0;

        while (resultLenBytes < sessionIdLength) {
            getRandomBytes(random);
            for (int j = 0;
            j < random.length && resultLenBytes < sessionIdLength;
            j++) {
                byte b1 = (byte) ((random[j] & 0xf0) >> 4);
                byte b2 = (byte) (random[j] & 0x0f);
                if (b1 < 10)
                    buffer.append((char) ('0' + b1));
                else
                    buffer.append((char) ('A' + (b1 - 10)));
                if (b2 < 10)
                    buffer.append((char) ('0' + b2));
                else
                    buffer.append((char) ('A' + (b2 - 10)));
                resultLenBytes++;
            }
        }

        if (route != null && route.length() > 0) {
            buffer.append('.').append(route);
        } else {
            String jvmRoute = getJvmRoute();
            if (jvmRoute != null && jvmRoute.length() > 0) {
                buffer.append('.').append(jvmRoute);
            }
        }

        return buffer.toString();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation performs the following checks:
     * <ul>
     * <li>The characters up to the first period (if any) are valid hex
     *     digits</li>
     * <li>There are at least enough hex digits to represent the specified
     *     session ID length</li>
     * <li>Anything after the first period is not validated since that is
     *     assumed to be a JVM route and we can't easily determine valid
     *     values</li>
     * </ul>
     */
    @Override
    public boolean validateSessionId(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        int len = sessionId.indexOf('.');
        if (len == -1) {
            len = sessionId.length();
        }
        // Session ID length is in bytes and 2 hex digits are required for each
        // byte
        if (len < getSessionIdLength() * 2) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (HexUtils.getDec(sessionId.charAt(i)) == -1) {
                return false;
            }
        }
        return true;
    }
}
