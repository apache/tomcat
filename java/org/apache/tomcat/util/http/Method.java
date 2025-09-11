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
package org.apache.tomcat.util.http;

public class Method {

    /*
     * This class was originally created to hold the bytes to String conversion method. It turns out that these
     * constants are just as much of a benefit to performance - if used consistently.
     *
     * If the String constants for the methods are used throughout the code-base, that allows String.equals() to use the
     * 'same object shortcut' when checking if a request is (or is not) using a particular method. That is faster than a
     * character by character comparison. That results in a further performance improvement that is as big - or possibly
     * slightly bigger - than the improvement obtained by using the optimised conversion.
     */

    // Standard HTTP methods supported by HttpServlet
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String PATCH = "PATCH";
    public static final String HEAD = "HEAD";
    public static final String OPTIONS = "OPTIONS";
    public static final String DELETE = "DELETE";
    public static final String TRACE = "TRACE";
    // Additional WebDAV methods
    public static final String PROPFIND = "PROPFIND";
    public static final String PROPPATCH = "PROPPATCH";
    public static final String MKCOL = "MKCOL";
    public static final String COPY = "COPY";
    public static final String MOVE = "MOVE";
    public static final String LOCK = "LOCK";
    public static final String UNLOCK = "UNLOCK";
    // Other methods recognised by Tomcat
    public static final String CONNECT = "CONNECT";


    /**
     * Provides optimised conversion from bytes to Strings for known HTTP methods. The bytes are assumed to be an
     * ISO-8859-1 encoded representation of an HTTP method. The method is not validated as being a token, but only valid
     * HTTP method names will be returned.
     * <p>
     * Doing it this way is ~10x faster than using MessageBytes.toStringType() saving ~40ns per request which is ~1% of
     * the processing time for a minimal "Hello World" type servlet. For non-standard methods there is an additional
     * overhead of ~2.5ns per request.
     * <p>
     * Pretty much every request ends up converting the method to a String so it is more efficient to do this straight
     * away and always use Strings.
     *
     * @param buf   The byte buffer containing the HTTP method to convert
     * @param start The first byte of the HTTP method
     * @param len   The number of bytes to convert
     *
     * @return The HTTP method as a String or {@code null} if the method is not recognised.
     */
    public static String bytesToString(byte[] buf, int start, int len) {
        switch (buf[start]) {
            case 'G': {
                if (len == 3 && buf[start + 1] == 'E' && buf[start + 2] == 'T') {
                    return GET;
                }
                break;
            }
            case 'P': {
                if (len == 4 && buf[start + 1] == 'O' && buf[start + 2] == 'S' && buf[start + 3] == 'T') {
                    return POST;
                } else if (len == 3 && buf[start + 1] == 'U' && buf[start + 2] == 'T') {
                    return PUT;
                } else if (len == 5 && buf[start + 1] == 'A' && buf[start + 2] == 'T' && buf[start + 3] == 'C' &&
                        buf[start + 4] == 'H') {
                    return PATCH;
                } else if (len == 8 && buf[start + 1] == 'R' && buf[start + 2] == 'O' && buf[start + 3] == 'P' &&
                        buf[start + 4] == 'F' && buf[start + 5] == 'I' && buf[start + 6] == 'N' &&
                        buf[start + 7] == 'D') {
                    return PROPFIND;
                } else if (len == 9 && buf[start + 1] == 'R' && buf[start + 2] == 'O' && buf[start + 3] == 'P' &&
                        buf[start + 4] == 'P' && buf[start + 5] == 'A' && buf[start + 6] == 'T' &&
                        buf[start + 7] == 'C' && buf[start + 8] == 'H') {
                    return PROPPATCH;
                }
                break;
            }
            case 'H': {
                if (len == 4 && buf[start + 1] == 'E' && buf[start + 2] == 'A' && buf[start + 3] == 'D') {
                    return HEAD;
                }
                break;
            }
            case 'O': {
                if (len == 7 && buf[start + 1] == 'P' && buf[start + 2] == 'T' && buf[start + 3] == 'I' &&
                        buf[start + 4] == 'O' && buf[start + 5] == 'N' && buf[start + 6] == 'S') {
                    return OPTIONS;
                }
                break;
            }
            case 'D': {
                if (len == 6 && buf[start + 1] == 'E' && buf[start + 2] == 'L' && buf[start + 3] == 'E' &&
                        buf[start + 4] == 'T' && buf[start + 5] == 'E') {
                    return DELETE;
                }
                break;
            }
            case 'T': {
                if (len == 5 && buf[start + 1] == 'R' && buf[start + 2] == 'A' && buf[start + 3] == 'C' &&
                        buf[start + 4] == 'E') {
                    return TRACE;
                }
                break;
            }
            case 'M': {
                if (len == 5 && buf[start + 1] == 'K' && buf[start + 2] == 'C' && buf[start + 3] == 'O' &&
                        buf[start + 4] == 'L') {
                    return MKCOL;
                } else if (len == 4 && buf[start + 1] == 'O' && buf[start + 2] == 'V' && buf[start + 3] == 'E') {
                    return MOVE;
                }
                break;
            }
            case 'C': {
                if (len == 4 && buf[start + 1] == 'O' && buf[start + 2] == 'P' && buf[start + 3] == 'Y') {
                    return COPY;
                } else if (len == 7 && buf[start + 1] == 'O' && buf[start + 2] == 'N' && buf[start + 3] == 'N' &&
                        buf[start + 4] == 'E' && buf[start + 5] == 'C' && buf[start + 6] == 'T') {
                    return CONNECT;
                }
                break;
            }
            case 'L': {
                if (len == 4 && buf[start + 1] == 'O' && buf[start + 2] == 'C' && buf[start + 3] == 'K') {
                    return LOCK;
                }
                break;
            }
            case 'U': {
                if (len == 6 && buf[start + 1] == 'N' && buf[start + 2] == 'L' && buf[start + 3] == 'O' &&
                        buf[start + 4] == 'C' && buf[start + 5] == 'K') {
                    return UNLOCK;
                }
                break;
            }
        }

        return null;
    }
}
