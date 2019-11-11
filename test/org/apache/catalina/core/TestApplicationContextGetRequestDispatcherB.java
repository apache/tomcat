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
package org.apache.catalina.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlet4preview.http.HttpServletMapping;
import org.apache.catalina.servlet4preview.http.MappingMatch;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(value = Parameterized.class)
public class TestApplicationContextGetRequestDispatcherB extends TomcatBaseTest {

    @Parameters(name = "{index}: startMapping[{0}], startUri[{1}], dispatcherType[{2}], " +
            "targetMapping[{3}], targetUri[{4}], useEncodedDispatchPaths[{5}], " +
            "expectedRequestURI[{6}], expectedContextPath[{7}], expectedServletPath[{8}], " +
            "expectedPathInfo[{9}], expectedQueryString[{10}], expectedMappingMatch[{11}, " +
            "expectedMappingPattern[{12}], expectedMappingMatchValue[{13}], " +
            "expectedMappingServletName[{14}], " +
            "expectedDispatcherRequestURI[{15}], expectedDispatcherContextPath[{16}], " +
            "expectedDispatcherServletPath[{17}], expectedDispatcherPathInfo[{18}], " +
            "expectedDispatcherQueryString[{19}], expectedDispatcherMappingMatch[{20}]," +
            "expectedDispatcherMappingPattern[{21}], expectedDispatcherMappingMatchValue[{22}]," +
            "expectedDispatcherMappingServletName[{23}]," +
            "expectedBody")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            // Simple dispatch for each type
            { "/start", "/start", DispatcherType.INCLUDE, "/target", "/target", Boolean.TRUE,
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start", "/start", DispatcherType.FORWARD, "/target", "/target", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start", DispatcherType.ASYNC, "/target", "/target", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Simple dispatch with query strings
            { "/start", "/start?abcde=fghij", DispatcherType.INCLUDE, "/target", "/target?zyxwv=utsrq", Boolean.TRUE,
                    "/test/start", "/test", "/start", null, "abcde=fghij",
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/target", "/test", "/target", null, "zyxwv=utsrq",
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start", "/start?abcde=fghij", DispatcherType.FORWARD, "/target", "/target?zyxwv=utsrq", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, "zyxwv=utsrq",
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, "abcde=fghij",
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start?abcde=fghij", DispatcherType.ASYNC, "/target", "/target?zyxwv=utsrq", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, "zyxwv=utsrq",
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, "abcde=fghij",
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Simple dispatch with trailing path parameters at start
            { "/start", "/start;abcde=fghij", DispatcherType.INCLUDE, "/target", "/target", Boolean.TRUE,
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start", "/start;abcde=fghij", DispatcherType.FORWARD, "/target", "/target", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start;abcde=fghij", DispatcherType.ASYNC, "/target", "/target", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Simple dispatch with path parameters at start
            { "/start", ";abcde=fghij/start", DispatcherType.INCLUDE, "/target", "/target", Boolean.TRUE,
                    "/test;abcde=fghij/start", "/test;abcde=fghij", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start", ";abcde=fghij/start", DispatcherType.FORWARD, "/target", "/target", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test;abcde=fghij/start", "/test;abcde=fghij", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", ";abcde=fghij/start", DispatcherType.ASYNC, "/target", "/target", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test;abcde=fghij/start", "/test;abcde=fghij", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Simple dispatch with path parameters on dispatch
            { "/start", "/start", DispatcherType.INCLUDE, "/target", "/target;abcde=fghij", Boolean.TRUE,
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/target;abcde=fghij", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start", "/start", DispatcherType.FORWARD, "/target", "/target;abcde=fghij", Boolean.TRUE,
                    "/test/target;abcde=fghij", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start", DispatcherType.ASYNC, "/target", "/target;abcde=fghij", Boolean.TRUE,
                    "/test/target;abcde=fghij", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Simple dispatch with multiple path parameters on start and dispatch
            { "/start", "/start;abcde=fghij", DispatcherType.INCLUDE, "/target", ";klmno=pqrst/target;uvwxy=z0123", Boolean.TRUE,
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/;klmno=pqrst/target;uvwxy=z0123", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start", "/start;abcde=fghij", DispatcherType.FORWARD, "/target", ";klmno=pqrst/target;uvwxy=z0123", Boolean.TRUE,
                    "/test/;klmno=pqrst/target;uvwxy=z0123", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start;abcde=fghij", DispatcherType.ASYNC, "/target", ";klmno=pqrst/target;uvwxy=z0123", Boolean.TRUE,
                    "/test/;klmno=pqrst/target;uvwxy=z0123", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "ASYNC-IAE"},
            // Simple dispatch with directory traversal
            { "/start/*", "/start/foo", DispatcherType.INCLUDE, "/target", "../target", Boolean.TRUE,
                    "/test/start/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "/test/start/../target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start/*", "/start/foo", DispatcherType.FORWARD, "/target", "../target", Boolean.TRUE,
                    "/test/start/../target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "OK"},
            { "/start/*", "/start/foo", DispatcherType.ASYNC, "/target", "../target", Boolean.TRUE,
                    "/test/start/../target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "ASYNC-IAE"},
            // Simple dispatch with directory traversal and path parameters
            // Note comments in Request.getRequestDispatcher(String) that
            // explain why the path parameter abcde=fghij is not present on the
            // dispatched requestURI
            { "/start/*", "/start;abcde=fghij/foo", DispatcherType.INCLUDE, "/target", "../target;klmno=pqrst", Boolean.TRUE,
                    "/test/start;abcde=fghij/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "/test/start/../target;klmno=pqrst", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "OK"},
            { "/start/*", "/start;abcde=fghij/foo", DispatcherType.FORWARD, "/target", "../target;klmno=pqrst", Boolean.TRUE,
                    "/test/start/../target;klmno=pqrst", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start;abcde=fghij/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "OK"},
            { "/start/*", "/start;abcde=fghij/foo", DispatcherType.ASYNC, "/target", "../target;klmno=pqrst", Boolean.TRUE,
                    "/test/start;abcde=fghij/../target;klmno=pqrst", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start;abcde=fghij/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "ASYNC-IAE"},
            // Simple dispatch with invalid directory traversal
            { "/start/*", "/start/foo", DispatcherType.INCLUDE, "/target", "../../target", Boolean.TRUE,
                    "/test/start/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "/test/start/../target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "RD-NULL"},
            { "/start/*", "/start/foo", DispatcherType.FORWARD, "/target", "../../target", Boolean.TRUE,
                    "/test/start/../target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "RD-NULL"},
            { "/start/*", "/start/foo", DispatcherType.ASYNC, "/target", "../../target", Boolean.TRUE,
                    "/test/start/../target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start/foo", "/test", "/start", "/foo", null,
                    MappingMatch.PATH, "/start/*", "foo", "rd",
                    "ASYNC-IAE"},
            // Simple dispatch with invalid target
            { "/start", "/start", DispatcherType.INCLUDE, "/target", "/does-not-exist", Boolean.TRUE,
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "RD-NULL"},
            { "/start", "/start", DispatcherType.FORWARD, "/target", "/does-not-exist", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "RD-NULL"},
            { "/start", "/start", DispatcherType.ASYNC, "/target", "/does-not-exist", Boolean.TRUE,
                    "/test/target", "/test", "/target", null, null,
                    MappingMatch.EXACT, "/target", "target", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "ASYNC-RD-NULL"},
            // Welcome files
            { "/start", "/start", DispatcherType.INCLUDE, "*.html", "/", Boolean.TRUE,
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "OK"},
            { "/start", "/start", DispatcherType.FORWARD, "*.html", "/", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start", DispatcherType.ASYNC, "*.html", "/", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Welcome files with query strings
            { "/start", "/start?abcde=fghij", DispatcherType.INCLUDE, "*.html", "/?zyxwv=utsrq", Boolean.TRUE,
                    "/test/start", "/test", "/start", null, "abcde=fghij",
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/", "/test", "/index.html", null, "zyxwv=utsrq",
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "OK"},
            { "/start", "/start?abcde=fghij", DispatcherType.FORWARD, "*.html", "/?zyxwv=utsrq", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, "zyxwv=utsrq",
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start", "/test", "/start", null, "abcde=fghij",
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start?abcde=fghij", DispatcherType.ASYNC, "*.html", "/?zyxwv=utsrq", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, "zyxwv=utsrq",
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start", "/test", "/start", null, "abcde=fghij",
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Welcome files with trailing path parameters at start
            { "/start", "/start;abcde=fghij", DispatcherType.INCLUDE, "*.html", "/", Boolean.TRUE,
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "OK"},
            { "/start", "/start;abcde=fghij", DispatcherType.FORWARD, "*.html", "/", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start;abcde=fghij", DispatcherType.ASYNC, "*.html", "/", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start;abcde=fghij", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Welcome files with path parameters at start
            { "/start", ";abcde=fghij/start", DispatcherType.INCLUDE, "*.html", "/", Boolean.TRUE,
                    "/test;abcde=fghij/start", "/test;abcde=fghij", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "OK"},
            { "/start", ";abcde=fghij/start", DispatcherType.FORWARD, "*.html", "/", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test;abcde=fghij/start", "/test;abcde=fghij", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", ";abcde=fghij/start", DispatcherType.ASYNC, "*.html", "/", Boolean.TRUE,
                    "/test/", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test;abcde=fghij/start", "/test;abcde=fghij", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            // Welcome files with trailing path parameters on dispatch
            { "/start", "/start", DispatcherType.INCLUDE, "*.html", "/;abcde=fghij", Boolean.TRUE,
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "/test/;abcde=fghij", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "OK"},
            { "/start", "/start", DispatcherType.FORWARD, "*.html", "/;abcde=fghij", Boolean.TRUE,
                    "/test/;abcde=fghij", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
            { "/start", "/start", DispatcherType.ASYNC, "*.html", "/;abcde=fghij", Boolean.TRUE,
                    "/test/;abcde=fghij", "/test", "/index.html", null, null,
                    MappingMatch.EXTENSION, "*.html", "index", "target",
                    "/test/start", "/test", "/start", null, null,
                    MappingMatch.EXACT, "/start", "start", "rd",
                    "OK"},
        });
    }

    // Inputs
    private final String startMapping;
    private final String startUri;
    private final DispatcherType dispatcherType;
    private final String targetMapping;
    private final String targetUri;
    private final boolean useEncodedDispatchPaths;
    // Outputs
    private final String expectedRequestURI;
    private final String expectedContextPath;
    private final String expectedServletPath;
    private final String expectedPathInfo;
    private final String expectedQueryString;
    private final MappingMatch expectedMappingMatch;
    private final String expectedMappingPattern;
    private final String expectedMappingMatchValue;
    private final String expectedMappingServletName;
    private final String expectedDispatcherRequestURI;
    private final String expectedDispatcherContextPath;
    private final String expectedDispatcherServletPath;
    private final String expectedDispatcherPathInfo;
    private final String expectedDispatcherQueryString;
    private final MappingMatch expectedDispatcherMappingMatch;
    private final String expectedDispatcherMappingPattern;
    private final String expectedDispatcherMappingMatchValue;
    private final String expectedDispatcherMappingServletName;
    private final String expectedBody;


    public TestApplicationContextGetRequestDispatcherB(String startMapping, String startUri,
            DispatcherType dispatcherType, String targetMapping, String targetUri,
            boolean useEncodedDispatchPaths,
            String expectedRequestURI, String expectedContextPath, String expectedServletPath,
            String expectedPathInfo, String expectedQueryString, MappingMatch expectedMappingMatch,
            String expectedMappingPattern, String expectedMappingMatchValue,
            String expectedMappingServletName,
            String expectedDispatcherRequestURI, String expectedDispatcherContextPath,
            String expectedDispatcherServletPath, String expectedDispatcherPathInfo,
            String expectedDispatcherQueryString, MappingMatch expectedDispatcherMappingMatch,
            String expectedDispatcherMappingPattern, String expectedDispatcherMappingMatchValue,
            String expectedDispatcherMappingServletName,
            String expectedBody) {
        this.startMapping = startMapping;
        this.startUri = startUri;
        this.dispatcherType = dispatcherType;
        this.targetMapping = targetMapping;
        this.targetUri = targetUri;
        this.useEncodedDispatchPaths = useEncodedDispatchPaths;
        this.expectedRequestURI = expectedRequestURI;
        this.expectedContextPath = expectedContextPath;
        this.expectedServletPath = expectedServletPath;
        this.expectedPathInfo = expectedPathInfo;
        this.expectedQueryString = expectedQueryString;
        this.expectedMappingMatch = expectedMappingMatch;
        this.expectedMappingPattern = expectedMappingPattern;
        this.expectedMappingMatchValue = expectedMappingMatchValue;
        this.expectedMappingServletName = expectedMappingServletName;
        this.expectedDispatcherRequestURI = expectedDispatcherRequestURI;
        this.expectedDispatcherContextPath = expectedDispatcherContextPath;
        this.expectedDispatcherServletPath = expectedDispatcherServletPath;
        this.expectedDispatcherPathInfo = expectedDispatcherPathInfo;
        this.expectedDispatcherQueryString = expectedDispatcherQueryString;
        this.expectedDispatcherMappingMatch = expectedDispatcherMappingMatch;
        this.expectedDispatcherMappingPattern = expectedDispatcherMappingPattern;
        this.expectedDispatcherMappingMatchValue = expectedDispatcherMappingMatchValue;
        this.expectedDispatcherMappingServletName = expectedDispatcherMappingServletName;
        this.expectedBody = expectedBody;
     }


    @Test
    public void doTest() throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("/test", null);
        ctx.setDispatchersUseEncodedPaths(useEncodedDispatchPaths);
        ctx.addWelcomeFile("index.html");

        // Add a target servlet to dispatch to
        Tomcat.addServlet(ctx, "target", new Target());
        ctx.addServletMappingDecoded(targetMapping, "target");

        Wrapper w = Tomcat.addServlet(ctx, "rd", new Dispatch());
        w.setAsyncSupported(true);
        ctx.addServletMappingDecoded(startMapping, "rd");

        tomcat.start();

        StringBuilder url = new StringBuilder("http://localhost:");
        url.append(getPort());
        url.append("/test");
        url.append(startUri);

        ByteChunk bc = getUrl(url.toString());
        String body = bc.toString();

        Assert.assertEquals(expectedBody, body);
    }


    private class Dispatch extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            if (dispatcherType == DispatcherType.INCLUDE) {
                RequestDispatcher rd = req.getRequestDispatcher(targetUri);
                if (rd == null) {
                    writeResponse(resp, "RD-NULL");
                } else {
                    rd.include(req, resp);
                }
            } else if (dispatcherType == DispatcherType.FORWARD) {
                RequestDispatcher rd = req.getRequestDispatcher(targetUri);
                if (rd == null) {
                    writeResponse(resp, "RD-NULL");
                } else {
                    rd.forward(req, resp);
                }
            } else if (dispatcherType == DispatcherType.ASYNC) {
                AsyncContext ac = req.startAsync();
                try {
                    ac.dispatch(targetUri);
                } catch (IllegalArgumentException iae) {
                    // targetUri is invalid?
                    if (!targetUri.startsWith("/")) {
                        // That'll do it.
                        ac.complete();
                        writeResponse(resp, "ASYNC-IAE");
                    } else {
                        // Not expected. Rethrow.
                        throw iae;
                    }
                } catch (UnsupportedOperationException uoe) {
                    // While a custom context implementation could cause this,
                    // if this occurs during this unit test the cause will be an
                    // invalid (unmapped) target path which returned a null
                    // dispatcher
                    ac.complete();
                    writeResponse(resp, "ASYNC-RD-NULL");
                }
            } else {
                // Unexpected dispatch type for this test
                throw new ServletException("Unknown dispatch type: " + dispatcherType);
            }
        }


        private void writeResponse(HttpServletResponse resp, String message) throws IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.print(message);
        }
    }


    private class Target extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            Assert.assertEquals(expectedRequestURI, req.getRequestURI());
            Assert.assertEquals(expectedContextPath, req.getContextPath());
            Assert.assertEquals(expectedServletPath, req.getServletPath());
            Assert.assertEquals(expectedPathInfo, req.getPathInfo());
            Assert.assertEquals(expectedQueryString, req.getQueryString());
            HttpServletMapping mapping =
                    ((org.apache.catalina.servlet4preview.http.HttpServletRequest) req).getHttpServletMapping();
            Assert.assertEquals(expectedMappingMatch, mapping.getMappingMatch());
            Assert.assertEquals(expectedMappingPattern, mapping.getPattern());
            Assert.assertEquals(expectedMappingMatchValue, mapping.getMatchValue());
            Assert.assertEquals(expectedMappingServletName, mapping.getServletName());

            for (DispatcherType type : DispatcherType.values()) {
                if (type == dispatcherType) {
                    String name = dispatcherType.name().toLowerCase(Locale.ENGLISH);
                    Assert.assertEquals(expectedDispatcherRequestURI,
                            req.getAttribute("javax.servlet." + name + ".request_uri"));
                    Assert.assertEquals(expectedDispatcherContextPath,
                            req.getAttribute("javax.servlet." + name + ".context_path"));
                    Assert.assertEquals(expectedDispatcherServletPath,
                            req.getAttribute("javax.servlet." + name + ".servlet_path"));
                    Assert.assertEquals(expectedDispatcherPathInfo,
                            req.getAttribute("javax.servlet." + name + ".path_info"));
                    Assert.assertEquals(expectedDispatcherQueryString,
                            req.getAttribute("javax.servlet." + name + ".query_string"));
                    HttpServletMapping dispatcherMapping =
                            (HttpServletMapping) ((org.apache.catalina.servlet4preview.http.HttpServletRequest) req).getAttribute(
                                    "javax.servlet." + name + ".mapping");
                    Assert.assertNotNull(dispatcherMapping);
                    Assert.assertEquals(expectedDispatcherMappingMatch,
                            dispatcherMapping.getMappingMatch());
                    Assert.assertEquals(expectedDispatcherMappingPattern,
                            dispatcherMapping.getPattern());
                    Assert.assertEquals(expectedDispatcherMappingMatchValue,
                            dispatcherMapping.getMatchValue());
                    Assert.assertEquals(expectedDispatcherMappingServletName,
                            dispatcherMapping.getServletName());
                } else if (type == DispatcherType.ERROR || type == DispatcherType.REQUEST) {
                    // Skip - not tested
                } else {
                    assertAllNull(req, type.name().toLowerCase(Locale.ENGLISH));
                }
            }

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.print("OK");
        }
    }


    private void assertAllNull(HttpServletRequest req, String type) {
        Assert.assertNull(req.getAttribute("javax.servlet." + type + ".request_uri"));
        Assert.assertNull(req.getAttribute("javax.servlet." + type + ".context_path"));
        Assert.assertNull(req.getAttribute("javax.servlet." + type + ".servlet_path"));
        Assert.assertNull(req.getAttribute("javax.servlet." + type + ".path_info"));
        Assert.assertNull(req.getAttribute("javax.servlet." + type + ".query_string"));
        Assert.assertNull(req.getAttribute("javax.servlet." + type + ".mapping"));
    }
}
