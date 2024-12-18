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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.FastHttpDateFormat;

/**
 * This test case is used to verify RFC 9110 Section 13. Conditional Requests.
 */
@RunWith(Parameterized.class)
public class TestDefaultServletRfc9110Section13 extends TomcatBaseTest {

    @Parameter(0)
    public boolean useStrongETags;
    @Parameter(1)
    public Task task;
    @Parameter(2)
    public EtagPrecondition ifMatchPrecondition;
    @Parameter(3)
    public DatePrecondition ifUnmodifiedSincePrecondition;
    @Parameter(4)
    public EtagPrecondition ifNoneMatchPrecondition;
    @Parameter(5)
    public DatePrecondition ifModifiedSincePrecondition;
    @Parameter(6)
    public EtagPrecondition ifRangeEtagPrecondition;
    @Parameter(7)
    public DatePrecondition ifRangeDatePrecondition;
    @Parameter(8)
    public boolean addRangeHeader;
    @Parameter(9)
    public Integer scExpected;

    @Parameterized.Parameters(name = "{index} resource-strong [{0}], matchHeader [{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        for (Boolean useStrongEtag : booleans) {
            for (Task task : Arrays.asList(Task.HEAD_INDEX_HTML, Task.GET_INDEX_HTML, Task.POST_INDEX_HTML)) {
                // RFC 9110, Section 13.2.2, Step 1, HEAD: If-Match with and without If-Unmodified-Since
                for (DatePrecondition dateCondition : DatePrecondition.values()) {
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.ALL, dateCondition, null,
                            null, null, null, Boolean.FALSE, SC_200 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.EXACTLY, dateCondition, null,
                            null, null, null, Boolean.FALSE, useStrongEtag.booleanValue() ? SC_200 : SC_412 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.IN, dateCondition, null,
                            null, null, null, Boolean.FALSE, useStrongEtag.booleanValue() ? SC_200 : SC_412 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.NOT_IN, dateCondition, null,
                            null, null, null, Boolean.FALSE, SC_412 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.INVALID, dateCondition, null,
                            null, null, null, Boolean.FALSE, SC_400 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.INVALID_ALL_PLUS_OTHER,
                            dateCondition, null, null, null, null, Boolean.FALSE, SC_400 });
                }

                // RFC 9110, Section 13.2.2, Step 2, HEAD: If-Unmodified-Since only
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.EQ, null, null, null, null,
                        Boolean.FALSE, SC_200 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.LT, null, null, null, null,
                        Boolean.FALSE, SC_412 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.GT, null, null, null, null,
                        Boolean.FALSE, SC_200 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.MULTI_IN, null, null, null,
                        null, Boolean.FALSE, SC_200 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.MULTI_IN_REV, null, null,
                        null, null, Boolean.FALSE, SC_200 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.INVALID, null, null, null,
                        null, Boolean.FALSE, SC_200 });

                // Ensure If-Unmodified-Since takes precedence over If-Modified-Since
                // If-Unmodified-Since only
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.LT, null, null, null, null,
                        Boolean.FALSE, SC_412 });
                // If-Modified-Since only
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.GT, null, null,
                        Boolean.FALSE, task.equals(Task.POST_INDEX_HTML) ? SC_200 : SC_304 });
                // Both
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.LT, null,
                        DatePrecondition.GT, null, null, Boolean.FALSE, SC_412 });

                // RFC 9110, Section 13.2.2, Step 3, HEAD: If-None-Match with and without If-Modified-Since
                for (DatePrecondition dateCondition : DatePrecondition.values()) {
                    parameterSets
                            .add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.ALL, dateCondition,
                                    null, null, Boolean.FALSE, task.equals(Task.POST_INDEX_HTML) ? SC_412 : SC_304 });
                    parameterSets.add(
                            new Object[] { useStrongEtag, task, null, null, EtagPrecondition.EXACTLY, dateCondition,
                                    null, null, Boolean.FALSE, task.equals(Task.POST_INDEX_HTML) ? SC_412 : SC_304 });
                    parameterSets
                            .add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.IN, dateCondition,
                                    null, null, Boolean.FALSE, task.equals(Task.POST_INDEX_HTML) ? SC_412 : SC_304 });
                    parameterSets.add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.NOT_IN,
                            dateCondition, null, null, Boolean.FALSE, SC_200 });
                    parameterSets.add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.INVALID,
                            dateCondition, null, null, Boolean.FALSE, SC_400 });
                    parameterSets.add(
                            new Object[] { useStrongEtag, task, null, null, EtagPrecondition.INVALID_ALL_PLUS_OTHER,
                                    dateCondition, null, null, Boolean.FALSE, SC_400 });
                }

                // RFC 9110, Section 13.2.2, Step 4, HEAD: If-Modified-Since only
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.EQ, null, null,
                        Boolean.FALSE, task.equals(Task.POST_INDEX_HTML) ? SC_200 : SC_304 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.LT, null, null,
                        Boolean.FALSE, SC_200 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.GT, null, null,
                        Boolean.FALSE, task.equals(Task.POST_INDEX_HTML) ? SC_200 : SC_304 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.MULTI_IN, null,
                        null, Boolean.FALSE, SC_200 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.MULTI_IN_REV,
                        null, null, Boolean.FALSE, SC_200 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.INVALID, null,
                        null, Boolean.FALSE, SC_200 });
            }

            for (Task task : Arrays.asList(Task.HEAD_404_HTML, Task.GET_404_HTML, Task.POST_404_HTML)) {
                // RFC 9110, Section 13.2.2, Step 1, HEAD: If-Match with and without If-Unmodified-Since
                for (DatePrecondition dateCondition : DatePrecondition.values()) {
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.ALL, dateCondition, null,
                            null, null, null, Boolean.FALSE, SC_412 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.IN, dateCondition, null,
                            null, null, null, Boolean.FALSE, SC_412 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.NOT_IN, dateCondition, null,
                            null, null, null, Boolean.FALSE, SC_412 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.INVALID, dateCondition, null,
                            null, null, null, Boolean.FALSE, SC_400 });
                    parameterSets.add(new Object[] { useStrongEtag, task, EtagPrecondition.INVALID_ALL_PLUS_OTHER,
                            dateCondition, null, null, null, null, Boolean.FALSE, SC_400 });
                }

                // RFC 9110, Section 13.2.2, Step 2, HEAD: If-Unmodified-Since only
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.EQ, null, null, null, null,
                        Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.LT, null, null, null, null,
                        Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.GT, null, null, null, null,
                        Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.MULTI_IN, null, null, null,
                        null, Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.MULTI_IN_REV, null, null,
                        null, null, Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, DatePrecondition.INVALID, null, null, null,
                        null, Boolean.FALSE, SC_404 });

                // RFC 9110, Section 13.2.2, Step 3, HEAD: If-None-Match with and without If-Modified-Since
                for (DatePrecondition dateCondition : DatePrecondition.values()) {
                    parameterSets.add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.ALL,
                            dateCondition, null, null, Boolean.FALSE, SC_404 });
                    parameterSets.add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.IN,
                            dateCondition, null, null, Boolean.FALSE, SC_404 });
                    parameterSets.add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.NOT_IN,
                            dateCondition, null, null, Boolean.FALSE, SC_404 });
                    parameterSets.add(new Object[] { useStrongEtag, task, null, null, EtagPrecondition.INVALID,
                            dateCondition, null, null, Boolean.FALSE, SC_400 });
                    parameterSets.add(
                            new Object[] { useStrongEtag, task, null, null, EtagPrecondition.INVALID_ALL_PLUS_OTHER,
                                    dateCondition, null, null, Boolean.FALSE, SC_400 });
                }

                // RFC 9110, Section 13.2.2, Step 4, HEAD: If-Modified-Since only
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.EQ, null, null,
                        Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.LT, null, null,
                        Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.GT, null, null,
                        Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.MULTI_IN, null,
                        null, Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.MULTI_IN_REV,
                        null, null, Boolean.FALSE, SC_404 });
                parameterSets.add(new Object[] { useStrongEtag, task, null, null, null, DatePrecondition.INVALID, null,
                        null, Boolean.FALSE, SC_404 });
            }

            // RFC 9110, Section 13.2.2, Step 5, GET: If-Range only
            // entity-tag
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.ALL, null, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.EXACTLY, null, Boolean.TRUE, useStrongEtag.booleanValue() ? SC_206 : SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.IN, null, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.NOT_IN, null, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.INVALID, null, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.INVALID_ALL_PLUS_OTHER, null, Boolean.TRUE, SC_400 });
            // HTTP-date
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.EQ, Boolean.TRUE, SC_206 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.LT, Boolean.TRUE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.GT, Boolean.TRUE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.MULTI_IN, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.MULTI_IN_REV, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.INVALID, Boolean.TRUE, SC_400 });
            // Range header without If-Range
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, EtagPrecondition.ALL, null, null, null,
                    null, null, Boolean.TRUE, SC_206 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, EtagPrecondition.EXACTLY, null, null,
                    null, null, null, Boolean.TRUE, useStrongEtag.booleanValue() ? SC_206 : SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, EtagPrecondition.IN, null, null, null,
                    null, null, Boolean.TRUE, useStrongEtag.booleanValue() ? SC_206 : SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, EtagPrecondition.NOT_IN, null, null,
                    null, null, null, Boolean.TRUE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, EtagPrecondition.INVALID, null, null,
                    null, null, null, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML,
                    EtagPrecondition.INVALID_ALL_PLUS_OTHER, null, null, null, null, null, Boolean.TRUE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, DatePrecondition.EQ, null, null,
                    null, null, Boolean.TRUE, SC_206 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, DatePrecondition.LT, null, null,
                    null, null, Boolean.TRUE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, DatePrecondition.GT, null, null,
                    null, null, Boolean.TRUE, SC_206 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, DatePrecondition.MULTI_IN, null,
                    null, null, null, Boolean.TRUE, SC_206 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, DatePrecondition.MULTI_IN_REV,
                    null, null, null, null, Boolean.TRUE, SC_206 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, DatePrecondition.INVALID, null,
                    null, null, null, Boolean.TRUE, SC_206 });
            // If-Range header without Range
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.ALL, null, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.EXACTLY, null, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.IN, null, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.NOT_IN, null, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.INVALID, null, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null,
                    EtagPrecondition.INVALID_ALL_PLUS_OTHER, null, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.EQ, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.LT, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.GT, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.MULTI_IN, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.MULTI_IN_REV, Boolean.FALSE, SC_200 });
            parameterSets.add(new Object[] { useStrongEtag, Task.GET_INDEX_HTML, null, null, null, null, null,
                    DatePrecondition.INVALID, Boolean.FALSE, SC_200 });

            // PUT tests
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_EXIST_TXT, null, null, null, null, null, null,
                    Boolean.FALSE, SC_204 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_EXIST_TXT, EtagPrecondition.ALL, null, null, null,
                    null, null, Boolean.FALSE, SC_204 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_EXIST_TXT, EtagPrecondition.EXACTLY, null, null,
                    null, null, null, Boolean.FALSE, useStrongEtag.booleanValue() ? SC_204 : SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_EXIST_TXT, EtagPrecondition.IN, null, null, null,
                    null, null, Boolean.FALSE, useStrongEtag.booleanValue() ? SC_204 : SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_EXIST_TXT, EtagPrecondition.NOT_IN, null, null,
                    null, null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_EXIST_TXT, EtagPrecondition.INVALID, null, null,
                    null, null, null, Boolean.FALSE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_EXIST_TXT, EtagPrecondition.INVALID_ALL_PLUS_OTHER,
                    null, null, null, null, null, Boolean.FALSE, SC_400 });

            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_NEW_TXT, null, null, null, null, null, null,
                    Boolean.FALSE, SC_201 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_NEW_TXT, EtagPrecondition.ALL, null, null, null,
                    null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_NEW_TXT, EtagPrecondition.IN, null, null, null,
                    null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_NEW_TXT, EtagPrecondition.NOT_IN, null, null, null,
                    null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_NEW_TXT, EtagPrecondition.INVALID, null, null,
                    null, null, null, Boolean.FALSE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.PUT_NEW_TXT, EtagPrecondition.INVALID_ALL_PLUS_OTHER,
                    null, null, null, null, null, Boolean.FALSE, SC_400 });

            // DELETE TESTS
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_EXIST_TXT, null, null, null, null, null, null,
                    Boolean.FALSE, SC_204 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_EXIST_TXT, EtagPrecondition.ALL, null, null,
                    null, null, null, Boolean.FALSE, SC_204 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_EXIST_TXT, EtagPrecondition.EXACTLY, null, null,
                    null, null, null, Boolean.FALSE, useStrongEtag.booleanValue() ? SC_204 : SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_EXIST_TXT, EtagPrecondition.IN, null, null,
                    null, null, null, Boolean.FALSE, useStrongEtag.booleanValue() ? SC_204 : SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_EXIST_TXT, EtagPrecondition.NOT_IN, null, null,
                    null, null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_EXIST_TXT, EtagPrecondition.INVALID, null, null,
                    null, null, null, Boolean.FALSE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_EXIST_TXT,
                    EtagPrecondition.INVALID_ALL_PLUS_OTHER, null, null, null, null, null, Boolean.FALSE, SC_400 });

            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_NOT_EXIST_TXT, null, null, null, null, null,
                    null, Boolean.FALSE, SC_404 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_NOT_EXIST_TXT, EtagPrecondition.ALL, null, null,
                    null, null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_NOT_EXIST_TXT, EtagPrecondition.IN, null, null,
                    null, null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_NOT_EXIST_TXT, EtagPrecondition.NOT_IN, null,
                    null, null, null, null, Boolean.FALSE, SC_412 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_NOT_EXIST_TXT, EtagPrecondition.INVALID, null,
                    null, null, null, null, Boolean.FALSE, SC_400 });
            parameterSets.add(new Object[] { useStrongEtag, Task.DELETE_NOT_EXIST_TXT,
                    EtagPrecondition.INVALID_ALL_PLUS_OTHER, null, null, null, null, null, Boolean.FALSE, SC_400 });
        }

        return parameterSets;
    }


    private static Integer SC_200 = Integer.valueOf(HttpServletResponse.SC_OK);
    private static Integer SC_201 = Integer.valueOf(HttpServletResponse.SC_CREATED);
    private static Integer SC_204 = Integer.valueOf(HttpServletResponse.SC_NO_CONTENT);
    private static Integer SC_206 = Integer.valueOf(HttpServletResponse.SC_PARTIAL_CONTENT);
    private static Integer SC_304 = Integer.valueOf(HttpServletResponse.SC_NOT_MODIFIED);
    private static Integer SC_400 = Integer.valueOf(HttpServletResponse.SC_BAD_REQUEST);
    private static Integer SC_404 = Integer.valueOf(HttpServletResponse.SC_NOT_FOUND);
    private static Integer SC_412 = Integer.valueOf(HttpServletResponse.SC_PRECONDITION_FAILED);


    private enum HTTP_METHOD {
        GET,
        PUT,
        DELETE,
        POST,
        HEAD
    }


    private enum Task {
        HEAD_INDEX_HTML(HTTP_METHOD.HEAD, "/index.html"),
        HEAD_404_HTML(HTTP_METHOD.HEAD, "/sc_404.html"),

        GET_INDEX_HTML(HTTP_METHOD.GET, "/index.html"),
        GET_404_HTML(HTTP_METHOD.GET, "/sc_404.html"),

        POST_INDEX_HTML(HTTP_METHOD.POST, "/index.html"),
        POST_404_HTML(HTTP_METHOD.POST, "/sc_404.html"),

        PUT_EXIST_TXT(HTTP_METHOD.PUT, "/put_exist.txt"),
        PUT_NEW_TXT(HTTP_METHOD.PUT, "/put_new.txt"),

        DELETE_EXIST_TXT(HTTP_METHOD.DELETE, "/delete_exist.txt"),
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


    private enum EtagPrecondition {
        EXACTLY,
        IN,
        ALL,
        NOT_IN,
        INVALID,
        INVALID_ALL_PLUS_OTHER
    }


    private enum DatePrecondition {
        /**
         * Condition header value of http date is equivalent to actual resource lastModified date
         */
        EQ,
        /**
         * Condition header value of http date is greater(later) than actual resource lastModified date
         */
        GT,
        /**
         * Condition header value of http date is less(earlier) than actual resource lastModified date
         */
        LT,
        MULTI_IN,
        MULTI_IN_REV,
        /**
         * not a valid HTTP-date
         */
        INVALID,
        /**
         * None.
         */
        NONE;
    }


    protected void genETagPrecondition(String strongETag, String weakETag, EtagPrecondition condition,
            String headerName, Map<String,List<String>> requestHeaders) {
        if (condition == null) {
            return;
        }
        List<String> headerValues = new ArrayList<>();
        switch (condition) {
            case ALL:
                headerValues.add("*");
                break;
            case EXACTLY:
                if (strongETag != null) {
                    headerValues.add(strongETag);
                } else {
                    // Should not happen
                    throw new IllegalArgumentException("strong etag not found!");
                }
                break;
            case IN:
                headerValues.add("\"1a2b3c4d\"");
                headerValues.add((weakETag != null ? weakETag + "," : "") +
                        (strongETag != null ? strongETag + "," : "") + "W/\"*\"");
                headerValues.add("\"abcdefg\"");
                break;
            case NOT_IN:
                headerValues.add("\"1a2b3c4d\"");
                if (weakETag != null && weakETag.length() > 8) {
                    headerValues.add(weakETag.substring(0, 3) + "XXXXX" + weakETag.substring(8));
                }
                if (strongETag != null && strongETag.length() > 6) {
                    headerValues.add(strongETag.substring(0, 1) + "XXXXX" + strongETag.substring(6));
                }
                headerValues.add("\"abcdefg\"");
                break;
            case INVALID:
                headerValues.add("invalid-no-quotes");
                break;
            case INVALID_ALL_PLUS_OTHER:
                headerValues.add("*");
                headerValues.add("W/\"1abcd\"");
                break;
        }
        if (!headerValues.isEmpty()) {
            requestHeaders.put(headerName, headerValues);
        }
    }


    protected void genDatePrecondition(long lastModifiedTimestamp, DatePrecondition condition, String headerName,
            Map<String,List<String>> requestHeaders) {
        if (condition == null || lastModifiedTimestamp <= 0) {
            return;
        }
        List<String> headerValues = new ArrayList<>();
        switch (condition) {
            case EQ:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp));
                break;
            case GT:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp + 30000L));
                break;
            case LT:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp - 30000L));
                break;
            case MULTI_IN:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp - 30000L));
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp));
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp + 30000L));
                break;
            case MULTI_IN_REV:
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp + 30000L));
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp));
                headerValues.add(FastHttpDateFormat.formatDate(lastModifiedTimestamp - 30000L));
                break;
            case INVALID:
                headerValues.add("2024.12.09 GMT");
                break;
            case NONE:
                // NO-OP
                break;
        }
        if (!headerValues.isEmpty()) {
            requestHeaders.put(headerName, headerValues);
        }
    }

    protected void addPreconditionHeaders(Map<String,List<String>> requestHeaders, String resourceETag,
            long lastModified) {

        String weakETag = resourceETag;
        String strongETag = resourceETag;
        if (resourceETag != null) {
            if (resourceETag.startsWith("W/")) {
                strongETag = resourceETag.substring(2);
            } else {
                weakETag = "W/" + resourceETag;
            }
        }

        genETagPrecondition(strongETag, weakETag, ifMatchPrecondition, "If-Match", requestHeaders);
        genDatePrecondition(lastModified, ifUnmodifiedSincePrecondition, "If-Unmodified-Since", requestHeaders);
        genETagPrecondition(strongETag, weakETag, ifNoneMatchPrecondition, "If-None-Match", requestHeaders);
        genDatePrecondition(lastModified, ifModifiedSincePrecondition, "If-Modified-Since", requestHeaders);
        genETagPrecondition(strongETag, weakETag, ifRangeEtagPrecondition, "If-Range", requestHeaders);
        genDatePrecondition(lastModified, ifRangeDatePrecondition, "If-Range", requestHeaders);
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

        if (task.m.equals(HTTP_METHOD.PUT)) {
            Files.write(Path.of(tempDocBase.getAbsolutePath(), "put_exist.txt"), "put_exist_v0".getBytes(),
                    StandardOpenOption.CREATE);
            Path.of(tempDocBase.getAbsolutePath(), "put_exist.txt").toFile().setLastModified(lastModified);
        }

        if (task.m.equals(HTTP_METHOD.DELETE)) {
            Files.write(Path.of(tempDocBase.getAbsolutePath(), "delete_exist.txt"), "delete_exist_v0".getBytes(),
                    StandardOpenOption.CREATE);
            Path.of(tempDocBase.getAbsolutePath(), "delete_exist.txt").toFile().setLastModified(lastModified);
        }
    }

    @Test
    public void testPreconditions() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctxt = tomcat.addContext("", tempDocBase.getAbsolutePath());

        Wrapper w = Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        w.addInitParameter("readonly", "false");
        w.addInitParameter("allowPartialPut", "true");
        w.addInitParameter("useStrongETags", Boolean.toString(useStrongETags));
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

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

        addPreconditionHeaders(requestHeaders, etag, lastModified);

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
        if (addRangeHeader) {
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
        boolean test = scExpected.intValue() == sc;
        Assert.assertTrue(
                "Failure - sc expected:%d, sc actual:%d, task:%s, \ntarget resource:(%s,%s), \nreq headers: %s, \nresp headers: %s"
                        .formatted(scExpected, Integer.valueOf(sc), task, etag,
                                FastHttpDateFormat.formatDate(lastModified), requestHeaders.toString(),
                                responseHeaders.toString()),
                test);
    }
}
