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

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.easymock.EasyMock;

public class TestRequestUtil {

    @Test
    public void testGetContextPathEncodedRoot() {
        doTestGetContextPathEncoded("", "/foo/bar", "");
    }


    @Test
    public void testGetContextPathEncodedSimple() {
        doTestGetContextPathEncoded("/examples", "/examples/async/async0", "/examples");
    }


    @Test
    public void testGetContextPathEncodedNoServletPath() {
        doTestGetContextPathEncoded("/examples", "/examples", "/examples");
    }


    @Test
    public void testGetContextPathEncodedMultiSegment() {
        doTestGetContextPathEncoded("/a/b", "/a/b/c/d", "/a/b");
    }


    @Test
    public void testGetContextPathEncodedPathParameters() {
        doTestGetContextPathEncoded("/examples", "/examples;a=b/async/async0", "/examples;a=b");
    }


    @Test
    public void testGetContextPathEncodedEncoded() {
        doTestGetContextPathEncoded("/ex amples", "/ex%20amples/async/async0", "/ex%20amples");
    }


    @Test
    public void testGetContextPathEncodedMultipleSlashes() {
        doTestGetContextPathEncoded("/examples", "/examples//async/async0", "/examples");
    }


    /*
     * The context path is reached via a path that is normalized by the container.
     */
    @Test
    public void testGetContextPathEncodedLeadingDotSegments() {
        doTestGetContextPathEncoded("/examples", "/a/b/../../examples/async/async0", "/a/b/../../examples");
    }


    /*
     * The first candidate that matches is followed by '..' so the context path must be longer. If it isn't, the
     * remainder of the URI is not within the context.
     */
    @Test
    public void testGetContextPathEncodedDotSegmentsAfterContext() {
        doTestGetContextPathEncoded("/examples", "/examples/../examples/async/async0", "/examples/../examples");
    }


    @Test
    public void testGetContextPathEncodedDotSegmentsAfterContextRepeated() {
        doTestGetContextPathEncoded("/examples", "/examples/../examples/../examples/async/async0",
                "/examples/../examples/../examples");
    }


    @Test
    public void testGetContextPathEncodedEncodedDotSegmentsAfterContext() {
        doTestGetContextPathEncoded("/examples", "/examples/%2e%2e/examples/async/async0",
                "/examples/%2e%2e/examples");
        doTestGetContextPathEncoded("/examples", "/examples/.%2E/examples/async/async0", "/examples/.%2E/examples");
    }


    @Test
    public void testGetContextPathEncodedDotSegmentsWithPathParameterAfterContext() {
        doTestGetContextPathEncoded("/examples", "/examples/..;x=y/examples/async/async0",
                "/examples/..;x=y/examples");
    }


    @Test
    public void testGetContextPathEncodedMultiSegmentDotSegmentsAfterContext() {
        doTestGetContextPathEncoded("/a/b", "/a/b/../b/c/d", "/a/b/../b");
    }


    /*
     * The dot segments that climb out of the context do not immediately follow the first match for the context path.
     */
    @Test
    public void testGetContextPathEncodedDotSegmentsLaterClimbOutOfContext() {
        doTestGetContextPathEncoded("/examples", "/examples/a/../../examples/async/async0",
                "/examples/a/../../examples");
        doTestGetContextPathEncoded("/examples", "/examples/a/b/c/../../../../examples/async/async0",
                "/examples/a/b/c/../../../../examples");
        doTestGetContextPathEncoded("/examples", "/examples/a/b/c/../../../../examples/a/../b",
                "/examples/a/b/c/../../../../examples");
    }


    /*
     * Enough dot segments to reach the context path but not to climb out of the context.
     */
    @Test
    public void testGetContextPathEncodedDotSegmentsReturnToContext() {
        doTestGetContextPathEncoded("/examples", "/examples/a/b/c/../../..", "/examples");
        doTestGetContextPathEncoded("/examples", "/examples/a/b/c/../../../d", "/examples");
    }


    /*
     * Dot segments that are within the servlet path / path info (do not climb out of the context) are not part of the
     * context path.
     */
    @Test
    public void testGetContextPathEncodedDotSegmentsWithinContext() {
        doTestGetContextPathEncoded("/examples", "/examples/foo/../bar", "/examples");
        doTestGetContextPathEncoded("/examples", "/examples/./bar", "/examples");
    }


    /*
     * If there is no longer candidate that matches, the first match is used rather than failing.
     */
    @Test
    public void testGetContextPathEncodedDotSegmentsNoLongerMatch() {
        doTestGetContextPathEncoded("/examples", "/examples/../x", "/examples");
        doTestGetContextPathEncoded("/examples", "/examples/..", "/examples");
        // More dot segments than there are segments (the request would be rejected before this point)
        doTestGetContextPathEncoded("/examples", "/examples/a/b/c/../../../../..", "/examples");
    }


    /*
     * Encoded solidus and reverse solidus. The remainder of the URI must be decoded using the connector's
     * configuration. If the encoded characters are passed through they are part of a path segment (not separators)
     * so there is no '..' segment and the remainder does not leave the context.
     */
    @Test
    public void testGetContextPathEncodedEncodedSolidusPassThrough() {
        doTestGetContextPathEncoded("/test", "/test/a%2f..%2f..%2ftest/target", "/test", "passthrough",
                "reject");
    }


    @Test
    public void testGetContextPathEncodedEncodedSolidusDecode() {
        // The encoded solidus are separators so the remainder does leave the context
        doTestGetContextPathEncoded("/test", "/test/a%2f..%2f..%2ftest/target", "/test/a%2f..%2f..%2ftest", "decode",
                "reject");
    }


    @Test
    public void testGetContextPathEncodedEncodedSolidusReject() {
        // Such requests are rejected before this method would be called. It must not fail.
        doTestGetContextPathEncoded("/test", "/test/a%2f..%2f..%2ftest/target", "/test", "reject", "reject");
    }


    @Test
    public void testGetContextPathEncodedEncodedReverseSolidusPassThrough() {
        doTestGetContextPathEncoded("/test", "/test/a%5c..%5c..%5ctest/target", "/test", "reject", "passthrough");
    }


    @Test
    public void testGetContextPathEncodedEncodedReverseSolidusDecode() {
        doTestGetContextPathEncoded("/test", "/test/a%5c..%5c..%5ctest/target", "/test/a%5c..%5c..%5ctest", "reject",
                "decode");
    }


    /*
     * The candidates for the context path must be decoded using the connector's configuration too. If the encoded
     * solidus are passed through, test%2fx is a single segment so /test/../test%2fx/.. does not return to /test.
     */
    @Test
    public void testGetContextPathEncodedEncodedSolidusPassThroughCandidates() {
        doTestGetContextPathEncoded("/test", "/test/../test%2fx/../test/target", "/test/../test%2fx/../test",
                "passthrough", "reject");
    }


    @Test
    public void testGetContextPathEncodedEncodedSolidusDecodeCandidates() {
        // test%2fx is test/x so /test/../test%2fx/.. does return to /test
        doTestGetContextPathEncoded("/test", "/test/../test%2fx/../test/target", "/test/../test",
                "decode", "reject");
    }


    @Test
    public void testGetContextPathEncodedEncodedReverseSolidusPassThroughCandidates() {
        doTestGetContextPathEncoded("/test", "/test/../test%5cx/../test/target", "/test/../test%5cx/../test", "reject",
                "passthrough");
    }


    @Test
    public void testGetContextPathEncodedEncodedReverseSolidusDecodeCandidates() {
        // The encoded reverse solidus is a separator so the context path ends at the first one that follows /test/../test
        doTestGetContextPathEncoded("/test", "/test/../test%5cx/../test/target", "/test/../test", "reject", "decode");
    }


    /*
     * If the connector decodes encoded solidus and/or encoded reverse solidus they are path separators so the context
     * path may end at one.
     */
    @Test
    public void testGetContextPathEncodedContextPathEndsAtEncodedSolidus() {
        doTestGetContextPathEncoded("/test", "/test%2fx/target", "/test", "decode", "reject");
        doTestGetContextPathEncoded("/test", "/test%5cx/target", "/test", "reject", "decode");
    }


    @Test
    public void testGetContextPathEncodedContextPathContainsEncodedSolidus() {
        doTestGetContextPathEncoded("/a/b", "/a%2fb/target", "/a%2fb", "decode", "reject");
        doTestGetContextPathEncoded("/a/b", "/a%5cb/target", "/a%5cb", "reject", "decode");
        doTestGetContextPathEncoded("/a/b/c", "/a%2fb%5cc/target", "/a%2fb%5cc", "decode", "decode");
    }


    /*
     * Path parameters are removed before the URI is decoded so an encoded solidus or reverse solidus within a path
     * parameter is not a separator.
     */
    @Test
    public void testGetContextPathEncodedEncodedSeparatorInPathParameter() {
        doTestGetContextPathEncoded("/test", "/test;v=%5cignored/target", "/test;v=%5cignored", "reject", "decode");
        doTestGetContextPathEncoded("/test", "/test;v=%2fignored/target", "/test;v=%2fignored", "decode", "reject");
        doTestGetContextPathEncoded("/test", "/test;a=%2f;b=%5c/target", "/test;a=%2f;b=%5c", "decode", "decode");
        doTestGetContextPathEncoded("/a/b", "/a;x=%2f/b;y=%5c/c", "/a;x=%2f/b;y=%5c", "decode", "decode");
    }


    @Test
    public void testGetContextPathEncodedEncodedSeparatorInPathParameterAfterContext() {
        doTestGetContextPathEncoded("/test", "/test/target;v=%2fx", "/test", "decode", "decode");
        doTestGetContextPathEncoded("/test", "/test/..;v=%2f/test/target", "/test/..;v=%2f/test", "decode", "decode");
    }


    @Test
    public void testGetContextPathEncodedPathParameterToEndOfUri() {
        doTestGetContextPathEncoded("/test", "/test;v=%2f", "/test;v=%2f", "decode", "decode");
    }


    @Test
    public void testGetContextPathEncodedEncodedSeparatorsNotDecoded() {
        // Neither is a separator unless the connector decodes it
        doTestGetContextPathEncoded("/test", "/test/x%2fy%5cz", "/test", "passthrough", "passthrough");
    }


    private static void doTestGetContextPathEncoded(String canonicalContextPath, String requestUri, String expected,
            String encodedSolidusHandling, String encodedReverseSolidusHandling) {
        Connector connector = new Connector();
        connector.setEncodedSolidusHandling(encodedSolidusHandling);
        connector.setEncodedReverseSolidusHandling(encodedReverseSolidusHandling);
        doTestGetContextPathEncoded(canonicalContextPath, requestUri, expected, connector);
    }


    private static void doTestGetContextPathEncoded(String canonicalContextPath, String requestUri, String expected) {
        doTestGetContextPathEncoded(canonicalContextPath, requestUri, expected, new Connector());
    }


    private static void doTestGetContextPathEncoded(String canonicalContextPath, String requestUri, String expected,
            Connector connector) {
        ServletContext servletContext = EasyMock.createMock(ServletContext.class);
        EasyMock.expect(servletContext.getContextPath()).andStubReturn(canonicalContextPath);
        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(request.getServletContext()).andStubReturn(servletContext);
        EasyMock.expect(request.getRequestURI()).andStubReturn(requestUri);
        EasyMock.replay(servletContext, request);

        Assert.assertEquals(requestUri, expected, RequestUtil.getContextPathEncoded(connector, request));
    }

}
