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
package org.apache.catalina.servlets;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.IntPredicate;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.FastHttpDateFormat;

public class TestDefaultServletRfc9110Section13 extends TomcatBaseTest {

    @Test
    public void testPreconditions2_2_1_head0() throws Exception {
        startServer(true);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_IN, null, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_NOT_IN, null, null, null, null, 412);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);
    }

    @Test
    public void testPreconditions2_2_1_head1() throws Exception {
        startServer(false);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, null, null, null, 412);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_IN, null, null, null, null, 412);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_NOT_IN, null, null, null, null, 412);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);
    }

    @Test
    public void testPreconditions2_2_2_head0() throws Exception {
        startServer(true);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_LT, null, null, null, 412);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_MULTI_IN, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_2_head1() throws Exception {
        startServer(false);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_LT, null, null, null, 412);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, null, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_MULTI_IN, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_3_head0() throws Exception {
        startServer(true);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_IN, null, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_EXACTLY, null, null, 304);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_ALL, null, null, 304);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_EQ, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_EXACTLY, null, null, 304);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_ALL, null, null, 304);
    }

    @Test
    public void testPreconditions2_2_3_head1() throws Exception {
        startServer(false);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_ALL, null, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_EXACTLY, null, null, 304,
                412);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_EQ, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_EXACTLY, null, null, 304);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_ALL, null, null, 304);
    }
    // @Test
    // public void testPreconditions2_2_4_head0() throws Exception {
    // startServer(true);
    // testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_EQ, null, 200);
    // testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_LT, null, 412);
    // testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_GT, null, 200);
    // testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_MULTI, null, 200);
    // }

    @Test
    public void testPreconditions2_2_4_head1() throws Exception {
        startServer(false);
        testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_EQ, null, 304);
        testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_LT, null, 200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_GT, null, 304);
        testPreconditions(Task.HEAD_INDEX_HTML, null, null, null, IfPolicy.DATE_MULTI_IN, null, 200);

        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_ALL, null, IfPolicy.ETAG_NOT_IN, IfPolicy.DATE_EQ, null,
                200);
        testPreconditions(Task.HEAD_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_EXACTLY, IfPolicy.DATE_GT,
                null, 304, 412);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_EQ, IfPolicy.ETAG_NOT_IN, IfPolicy.DATE_LT, null,
                200);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_EXACTLY, IfPolicy.DATE_MULTI_IN,
                null, 304);
        testPreconditions(Task.HEAD_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_ALL, IfPolicy.DATE_EQ, null, 304);
    }

    @Test
    public void testPreconditions2_2_1_get0() throws Exception {
        startServer(true);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_1_get1() throws Exception {
        startServer(false);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, 200);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, null, null, null, 412);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_IN, null, null, null, null, 412);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_NOT_IN, null, null, null, null, 412);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);
    }

    @Test
    public void testPreconditions2_2_2_get0() throws Exception {
        startServer(true);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, null, 200);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_LT, null, null, null, 412);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_GT, null, null, null, 200);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_MULTI_IN, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_2_get1() throws Exception {
        startServer(false);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, null, 200);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_LT, null, null, null, 412);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_GT, null, null, null, 200);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_MULTI_IN, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_5_get0() throws Exception {
        startServer(true);
        testPreconditions(Task.GET_INDEX_HTML, null, null, null, null, IfPolicy.DATE_EQ, true, 206);
        // if-range: multiple node policy, not defined in RFC 9110.
        // Currently, tomcat process the first If-Range header simply.
        // testPreconditions(Task.GET_INDEX_HTML, null, null, null, null, IfPolicy.DATE_MULTI_IN, true,200);
        testPreconditions(Task.GET_INDEX_HTML, null, null, null, null, IfPolicy.DATE_SEMANTIC_INVALID, true, 200);
        testPreconditions(Task.GET_INDEX_HTML, null, null, null, null, IfPolicy.ETAG_EXACTLY, true, 206);

        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_EQ, true, 206);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_LT, true, 200);
        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_GT, true, 200);

        testPreconditions(Task.GET_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_EQ, false, 200);

        // Test Range header is present, while if-range is not.
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, true, 206);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, null, null, null, true, 206);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_IN, null, null, null, null, true, 206);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_NOT_IN, null, null, null, null, true, 412);
        testPreconditions(Task.GET_INDEX_HTML, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, true, 400);
    }


    @Test
    public void testPreconditions2_2_1_post0() throws Exception {
        startServer(true);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_1_post1() throws Exception {
        startServer(false);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, null, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);
    }

    @Test
    public void testPreconditions2_2_2_post0() throws Exception {
        startServer(true);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_LT, null, null, null, false, null,
                k -> ((k >= 200 && k < 300) || k == 412), -1);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_MULTI_IN, null, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_SEMANTIC_INVALID, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_2_post1() throws Exception {
        startServer(false);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_LT, null, null, null, false, null,
                k -> (k >= 200 && k < 300) || k == 412, -1);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_MULTI_IN, null, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_SEMANTIC_INVALID, null, null, null, 200);
    }

    @Test
    public void testPreconditions2_2_3_post0() throws Exception {
        startServer(true);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_EXACTLY, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_ALL, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_IN, null, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_EXACTLY, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_ALL, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_EXACTLY, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_ALL, null, null, 412);
    }

    @Test
    public void testPreconditions2_2_3_post1() throws Exception {
        startServer(false);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_EXACTLY, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_ALL, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_ALL, null, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_EXACTLY, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, IfPolicy.ETAG_ALL, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, IfPolicy.ETAG_NOT_IN, null, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_EXACTLY, null, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_GT, IfPolicy.ETAG_ALL, null, null, 412);
    }

    @Test
    public void testPreconditions2_2_4_post1() throws Exception {
        startServer(false);
        testPreconditions(Task.POST_INDEX_HTML, null, null, null, IfPolicy.DATE_EQ, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, null, IfPolicy.DATE_LT, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, null, IfPolicy.DATE_GT, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, null, IfPolicy.DATE_MULTI_IN, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_NOT_IN, IfPolicy.DATE_EQ, null, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_EXACTLY, IfPolicy.DATE_LT, null, 412);
        testPreconditions(Task.POST_INDEX_HTML, null, null, IfPolicy.ETAG_ALL, IfPolicy.DATE_MULTI_IN, null, 412);
    }

    @Test
    public void testPreconditions2_2_5_post0() throws Exception {
        startServer(true);
        testPreconditions(Task.POST_INDEX_HTML, null, null, null, null, IfPolicy.DATE_EQ, true, 200);
        // if-range: multiple node policy, not defined in RFC 9110.
        // Currently, tomcat process the first If-Range header simply.
        // testPreconditions(Task.GET_INDEX_HTML, null, null, null, null, IfPolicy.DATE_MULTI_IN, true,200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, null, null, IfPolicy.DATE_SEMANTIC_INVALID, true, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, null, null, null, IfPolicy.ETAG_EXACTLY, true, 200);

        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_EQ, true, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_LT, true, 200);
        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_GT, true, 200);

        testPreconditions(Task.POST_INDEX_HTML, null, IfPolicy.DATE_EQ, null, null, IfPolicy.DATE_EQ, false, 200);

        // Test Range header is present, while if-range is not.
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_ALL, null, null, null, null, true, 200);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_EXACTLY, null, null, null, null, true, 200);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_IN, null, null, null, null, true, 200);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_NOT_IN, null, null, null, null, true, 412);
        testPreconditions(Task.POST_INDEX_HTML, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, true, 400);
    }

    @Ignore
    @Test
    public void testPreconditions2_2_1_put0() throws Exception {
        startServer(true);
        testPreconditions(Task.PUT_EXIST_TXT, IfPolicy.ETAG_ALL, null, null, null, null,
                HttpServletResponse.SC_NO_CONTENT);
        testPreconditions(Task.PUT_EXIST_TXT, IfPolicy.ETAG_IN, null, null, null, null,
                HttpServletResponse.SC_NO_CONTENT);
        testPreconditions(Task.PUT_EXIST_TXT, IfPolicy.ETAG_NOT_IN, null, null, null, null, 412);
        testPreconditions(Task.PUT_EXIST_TXT, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);

        testPreconditions(Task.PUT_NEW_TXT, null, null, null, null, null, HttpServletResponse.SC_CREATED);
    }

    @Ignore
    @Test
    public void testPreconditions2_2_1_put1() throws Exception {
        startServer(false);
        testPreconditions(Task.PUT_EXIST_TXT, IfPolicy.ETAG_ALL, null, null, null, null,
                HttpServletResponse.SC_NO_CONTENT);
        testPreconditions(Task.PUT_EXIST_TXT, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);
        testPreconditions(Task.PUT_EXIST_TXT, IfPolicy.ETAG_EXACTLY, null, null, null, null, 412);
    }

    @Ignore
    @Test
    public void testPreconditions2_2_1_delete0() throws Exception {
        startServer(true);
        testPreconditions(Task.DELETE_EXIST1_TXT, IfPolicy.ETAG_ALL, null, null, null, null,
                HttpServletResponse.SC_NO_CONTENT);
        testPreconditions(Task.DELETE_EXIST2_TXT, IfPolicy.ETAG_IN, null, null, null, null,
                HttpServletResponse.SC_NO_CONTENT);
        testPreconditions(Task.DELETE_EXIST3_TXT, IfPolicy.ETAG_NOT_IN, null, null, null, null, 412);
        testPreconditions(Task.DELETE_EXIST4_TXT, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);

        testPreconditions(Task.DELETE_NOT_EXIST_TXT, null, null, null, null, null, 404);
    }

    @Ignore
    @Test
    public void testPreconditions2_2_1_delete1() throws Exception {
        startServer(false);
        testPreconditions(Task.DELETE_EXIST1_TXT, IfPolicy.ETAG_ALL, null, null, null, null,
                HttpServletResponse.SC_NO_CONTENT);
        testPreconditions(Task.DELETE_EXIST3_TXT, IfPolicy.ETAG_EXACTLY, null, null, null, null, 412);
        testPreconditions(Task.DELETE_EXIST2_TXT, IfPolicy.ETAG_SYNTAX_INVALID, null, null, null, null, 400);
    }

    enum HTTP_METHOD {
        GET,
        PUT,
        DELETE,
        POST,
        HEAD
    }

    enum Task {
        HEAD_INDEX_HTML(HTTP_METHOD.HEAD, "/index.html"),
        HEAD_404_HTML(HTTP_METHOD.HEAD, "/sc_404.html"),

        GET_INDEX_HTML(HTTP_METHOD.GET, "/index.html"),
        GET_404_HTML(HTTP_METHOD.GET, "/sc_404.html"),

        POST_INDEX_HTML(HTTP_METHOD.POST, "/index.html"),
        POST_404_HTML(HTTP_METHOD.POST, "/sc_404.html"),

        PUT_EXIST_TXT(HTTP_METHOD.PUT, "/put_exist.txt"),
        PUT_NEW_TXT(HTTP_METHOD.PUT, "/put_new.txt"),

        DELETE_EXIST_TXT(HTTP_METHOD.DELETE, "/delete_exist.txt"),
        DELETE_EXIST1_TXT(HTTP_METHOD.DELETE, "/delete_exist1.txt"),
        DELETE_EXIST2_TXT(HTTP_METHOD.DELETE, "/delete_exist2.txt"),
        DELETE_EXIST3_TXT(HTTP_METHOD.DELETE, "/delete_exist3.txt"),
        DELETE_EXIST4_TXT(HTTP_METHOD.DELETE, "/delete_exist4.txt"),
        DELETE_NOT_EXIST_TXT(HTTP_METHOD.DELETE, "/delete_404.txt");

        HTTP_METHOD m;
        String uri;

        Task(HTTP_METHOD m, String uri) {
            this.m = m;
            this.uri = uri;
        }

        @Override
        public String toString() {
            return m.name() + " " + uri;
        }
    }

    enum IfPolicy {
        ETAG_EXACTLY,
        ETAG_IN,
        ETAG_ALL,
        ETAG_NOT_IN,
        ETAG_SYNTAX_INVALID,
        /**
         * Condition header value of http date is equivalent to actual resource lastModified date
         */
        DATE_EQ,
        /**
         * Condition header value of http date is greater(later) than actual resource lastModified date
         */
        DATE_GT,
        /**
         * Condition header value of http date is less(earlier) than actual resource lastModified date
         */
        DATE_LT,
        DATE_MULTI_IN,
        /**
         * not a valid HTTP-date
         */
        DATE_SEMANTIC_INVALID;
    }

    enum IfType {
        ifMatch("If-Match"), // ETag strong comparison
        ifUnmodifiedSince("If-Unmodified-Since"),
        ifNoneMatch("If-None-Match"), // ETag weak comparison
        ifModifiedSince("If-Modified-Since"),
        ifRange("If-Range"); // ETag strong comparison

        private String header;

        IfType(String header) {
            this.header = header;
        }

        public String value() {
            return this.header;
        }
    }

    protected List<String> genETagCondtion(String strongETag, String weakETag, IfPolicy policy) {
        List<String> headerValues = new ArrayList<>();
        switch (policy) {
            case ETAG_ALL:
                headerValues.add("*");
                break;
            case ETAG_EXACTLY:
                if (strongETag != null) {
                    headerValues.add(strongETag);
                } else {
                    // Should not happen
                    throw new IllegalArgumentException("strong etag not found!");
                }
                break;
            case ETAG_IN:
                headerValues.add("\"1a2b3c4d\"");
                headerValues.add(weakETag + "," + strongETag + ",W/\"*\"");
                headerValues.add("\"abcdefg\"");
                break;
            case ETAG_NOT_IN:
                if (weakETag != null && weakETag.length() > 8) {
                    headerValues.add(weakETag.substring(0, 3) + "XXXXX" + weakETag.substring(8));
                }
                if (strongETag != null && strongETag.length() > 6) {
                    headerValues.add(strongETag.substring(0, 1) + "XXXXX" + strongETag.substring(6));
                }
                break;
            case ETAG_SYNTAX_INVALID:
                headerValues.add("*");
                headerValues.add("W/\"1abcd\"");
                break;
            default:
                break;
        }
        return headerValues;
    }

    protected List<String> genDateCondtion(long lastModifiedTimestamp, IfPolicy policy) {
        List<String> headerValues = new ArrayList<>();
        if (lastModifiedTimestamp <= 0) {
            return headerValues;
        }
        switch (policy) {
            case DATE_EQ:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp));
                break;
            case DATE_GT:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp + 30000L));
                break;
            case DATE_LT:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp - 30000L));
                break;
            case DATE_MULTI_IN:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp - 30000L));
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp));
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp + 30000L));
                break;
            case DATE_SEMANTIC_INVALID:
                headerValues.add("2024.12.09 GMT");
                break;
            default:
                break;
        }
        return headerValues;
    }

    protected void wrapperHeaders(Map<String,List<String>> headers, String resourceETag, long lastModified,
            IfPolicy policy, IfType type) {
        Objects.requireNonNull(type);
        if (policy == null) {
            return;
        }
        List<String> headerValues = new ArrayList<>();
        String weakETag = resourceETag;
        String strongETag = resourceETag;
        if (resourceETag != null) {
            if (resourceETag.startsWith("W/")) {
                strongETag = resourceETag.substring(2);
            } else {
                weakETag = "W/" + resourceETag;
            }
        }

        List<String> eTagConditions = genETagCondtion(strongETag, weakETag, policy);
        if (!eTagConditions.isEmpty()) {
            headerValues.addAll(eTagConditions);
        }

        List<String> dateConditions = genDateCondtion(lastModified, policy);
        if (!dateConditions.isEmpty()) {
            headerValues.addAll(dateConditions);
        }

        if (!headerValues.isEmpty()) {
            headers.put(type.value(), headerValues);
        }
    }

    private File tempDocBase = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        tempDocBase = Files.createTempDirectory(getTemporaryDirectory().toPath(), "conditional").toFile();
        long lastModified = FastHttpDateFormat.parseDate("Fri, 06 Dec 2024 00:00:00 GMT");
        Files.write(Path.of(tempDocBase.getAbsolutePath(), "index.html"), "<html><body>Index</body></html>".getBytes(),
                StandardOpenOption.CREATE);
        Path.of(tempDocBase.getAbsolutePath(), "index.html").toFile().setLastModified(lastModified);

        Files.write(Path.of(tempDocBase.getAbsolutePath(), "put_exist.txt"), "put_exist_v0".getBytes(),
                StandardOpenOption.CREATE);
        Path.of(tempDocBase.getAbsolutePath(), "put_exist.txt").toFile().setLastModified(lastModified);

        Files.write(Path.of(tempDocBase.getAbsolutePath(), "delete_exist.txt"), "delete_exist_v0".getBytes(),
                StandardOpenOption.CREATE);
        Path.of(tempDocBase.getAbsolutePath(), "delete_exist.txt").toFile().setLastModified(lastModified);

        Files.write(Path.of(tempDocBase.getAbsolutePath(), "delete_exist1.txt"), "delete_exist1_v0".getBytes(),
                StandardOpenOption.CREATE);
        Path.of(tempDocBase.getAbsolutePath(), "delete_exist1.txt").toFile().setLastModified(lastModified);

        Files.write(Path.of(tempDocBase.getAbsolutePath(), "delete_exist2.txt"), "delete_exist2_v0".getBytes(),
                StandardOpenOption.CREATE);
        Path.of(tempDocBase.getAbsolutePath(), "delete_exist2.txt").toFile().setLastModified(lastModified);

        Files.write(Path.of(tempDocBase.getAbsolutePath(), "delete_exist3.txt"), "delete_exist3_v0".getBytes(),
                StandardOpenOption.CREATE);
        Path.of(tempDocBase.getAbsolutePath(), "delete_exist3.txt").toFile().setLastModified(lastModified);

        Files.write(Path.of(tempDocBase.getAbsolutePath(), "delete_exist4.txt"), "delete_exist4_v0".getBytes(),
                StandardOpenOption.CREATE);
        Path.of(tempDocBase.getAbsolutePath(), "delete_exist4.txt").toFile().setLastModified(lastModified);

    }

    protected void startServer(boolean resourceHasStrongETag) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctxt = tomcat.addContext("", tempDocBase.getAbsolutePath());

        Wrapper w = Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        w.addInitParameter("readonly", "false");
        w.addInitParameter("allowPartialPut", Boolean.toString(true));
        w.addInitParameter("useStrongETags", Boolean.toString(resourceHasStrongETag));
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();
    }


    protected void testPreconditions(Task task, IfPolicy ifMatchHeader, IfPolicy ifUnmodifiedSinceHeader,
            IfPolicy ifNoneMatchHeader, IfPolicy ifModifiedSinceHeader, IfPolicy ifRangeHeader, boolean autoRangeHeader,
            String message, IntPredicate p, int... scExpected) throws Exception {
        Assert.assertNotNull(task);


        Map<String,List<String>> requestHeaders = new HashMap<>();

        Map<String,List<String>> responseHeaders = new HashMap<>();

        String etag = null;
        long lastModified = -1;
        String uri = "http://localhost:" + getPort() + task.uri;
        // Try head to receives etag and lastModified Date
        int sc = headUrl(uri, new ByteChunk(), responseHeaders);
        if (sc == 200) {
            etag = getSingleHeader("ETag", responseHeaders);
            String dt = getSingleHeader("Last-Modified", responseHeaders);
            if (dt != null && dt.length() > 0) {
                lastModified = FastHttpDateFormat.parseDate(dt);
            }
        }

        wrapperHeaders(requestHeaders, etag, lastModified, ifMatchHeader, IfType.ifMatch);
        wrapperHeaders(requestHeaders, etag, lastModified, ifModifiedSinceHeader, IfType.ifModifiedSince);
        wrapperHeaders(requestHeaders, etag, lastModified, ifNoneMatchHeader, IfType.ifNoneMatch);
        wrapperHeaders(requestHeaders, etag, lastModified, ifUnmodifiedSinceHeader, IfType.ifUnmodifiedSince);
        wrapperHeaders(requestHeaders, etag, lastModified, ifRangeHeader, IfType.ifRange);
        responseHeaders.clear();
        sc = 0;
        SimpleHttpClient client = null;
        client = new SimpleHttpClient() {

            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        StringBuffer curl = new StringBuffer();
        curl.append(task.m.name() + " " + task.uri + " HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost" +
                SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF);

        for (Entry<String,List<String>> e : requestHeaders.entrySet()) {
            for (String v : e.getValue()) {
                curl.append(e.getKey() + ": " + v + SimpleHttpClient.CRLF);
            }
        }
        if (autoRangeHeader) {
            curl.append("Range: bytes=0-10" + SimpleHttpClient.CRLF);
        }
        curl.append("Content-Length: 6" + SimpleHttpClient.CRLF);
        curl.append(SimpleHttpClient.CRLF);

        curl.append("PUT_v2");
        client.setRequest(new String[] { curl.toString() });
        client.connect();
        client.processRequest();
        for (String e : client.getResponseHeaders()) {
            Assert.assertTrue("Separator ':' expected and not the last char of response header field `" + e + "`",
                    e.contains(":") && e.indexOf(':') < e.length() - 1);
            String name = e.substring(0, e.indexOf(':'));
            String value = e.substring(e.indexOf(':') + 1);
            responseHeaders.computeIfAbsent(name, k -> new ArrayList<String>()).add(value);
        }
        sc = client.getStatusCode();
        if (message == null) {
            message = "Unexpected status code:`" + sc + "`";
        }
        boolean test = false;
        boolean usePredicate = false;
        if (scExpected != null && scExpected.length > 0 && scExpected[0] >= 100) {
            test = Arrays.binarySearch(scExpected, sc) >= 0;
        } else {
            usePredicate = true;
            test = p.test(sc);
        }
        String scExpectation = usePredicate ? "IntPredicate" : Arrays.toString(scExpected);
        Assert.assertTrue(
                "Failure - sc expected:%s, sc actual:%d, %s, task:%s, \ntarget resource:(%s,%s), \nreq headers: %s, \nresp headers: %s"
                        .formatted(scExpectation, Integer.valueOf(sc), message, task, etag,
                                FastHttpDateFormat.formatDate(lastModified), requestHeaders.toString(),
                                responseHeaders.toString()),
                test);
    }

    protected void testPreconditions(Task task, IfPolicy ifMatchHeader, IfPolicy ifUnmodifiedSinceHeader,
            IfPolicy ifNoneMatchHeader, IfPolicy ifModifiedSinceHeader, IfPolicy ifRangeHeader, int... scExpected)
            throws Exception {
        testPreconditions(task, ifMatchHeader, ifUnmodifiedSinceHeader, ifNoneMatchHeader, ifModifiedSinceHeader,
                ifRangeHeader, false, scExpected);
    }

    protected void testPreconditions(Task task, IfPolicy ifMatchHeader, IfPolicy ifUnmodifiedSinceHeader,
            IfPolicy ifNoneMatchHeader, IfPolicy ifModifiedSinceHeader, IfPolicy ifRangeHeader, boolean autoRangeHeader,
            int... scExpected) throws Exception {
        testPreconditions(task, ifMatchHeader, ifUnmodifiedSinceHeader, ifNoneMatchHeader, ifModifiedSinceHeader,
                ifRangeHeader, autoRangeHeader, null, null, scExpected);
    }
}
