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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.easymock.EasyMock;

public class TestParameterLimitValve {

    private ParameterLimitValve valve;
    private Request request;
    private Response response;
    private ValveBase next;

    @Before
    public void setUp() {
        valve = new ParameterLimitValve();
        request = EasyMock.createMock(Request.class);
        response = EasyMock.createMock(Response.class);
        next = EasyMock.createMock(ValveBase.class);
        valve.setNext(next);
    }

    @Test
    public void testGlobalParameterLimitExceeded() throws IOException, ServletException {
        valve.setMaxGlobalParams(2);

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("param1", new String[]{"value1"});
        parameters.put("param2", new String[]{"value2"});
        parameters.put("param3", new String[]{"value3"});

        EasyMock.expect(request.getRequestURI()).andReturn("/some/uri");
        EasyMock.expect(request.getParameterMap()).andReturn(parameters);
        response.sendError(Response.SC_BAD_REQUEST, "Too many parameters for this URL: [/some/uri]");
        EasyMock.expectLastCall();

        EasyMock.replay(request, response);

        valve.invoke(request, response);

        EasyMock.verify(request, response);
    }

    @Test
    public void testGlobalParameterLimitNotExceeded() throws IOException, ServletException {
        valve.setMaxGlobalParams(3);

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("param1", new String[]{"value1"});
        parameters.put("param2", new String[]{"value2"});

        EasyMock.expect(request.getRequestURI()).andReturn("/some/uri");
        EasyMock.expect(request.getParameterMap()).andReturn(parameters);
        next.invoke(request, response);
        EasyMock.expectLastCall();

        EasyMock.replay(request, response, next);

        valve.invoke(request, response);

        EasyMock.verify(request, response, next);
    }

    @Test
    public void testSpecificUrlPatternLimitExceeded() throws IOException, ServletException {
        valve.setMaxGlobalParams(100);
        valve.setUrlPatternLimits("/special/.*=2");

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("param1", new String[]{"value1"});
        parameters.put("param2", new String[]{"value2"});
        parameters.put("param3", new String[]{"value3"});

        EasyMock.expect(request.getRequestURI()).andReturn("/special/endpoint");
        EasyMock.expect(request.getParameterMap()).andReturn(parameters);
        response.sendError(Response.SC_BAD_REQUEST, "Too many parameters for this URL: [/special/endpoint]");
        EasyMock.expectLastCall();

        EasyMock.replay(request, response);

        valve.invoke(request, response);

        EasyMock.verify(request, response);
    }

    @Test
    public void testSpecificUrlPatternLimitNotExceeded() throws IOException, ServletException {
        valve.setMaxGlobalParams(100);
        valve.setUrlPatternLimits("/special/.*=3");

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("param1", new String[]{"value1"});
        parameters.put("param2", new String[]{"value2"});

        EasyMock.expect(request.getRequestURI()).andReturn("/special/endpoint");
        EasyMock.expect(request.getParameterMap()).andReturn(parameters);
        next.invoke(request, response);
        EasyMock.expectLastCall();

        EasyMock.replay(request, response, next);

        valve.invoke(request, response);

        EasyMock.verify(request, response, next);
    }

    @Test
    public void testNoMatchingPatternWithGlobalLimit() throws IOException, ServletException {
        valve.setMaxGlobalParams(2);

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("param1", new String[]{"value1"});
        parameters.put("param2", new String[]{"value2"});

        EasyMock.expect(request.getRequestURI()).andReturn("/other/endpoint");
        EasyMock.expect(request.getParameterMap()).andReturn(parameters);
        next.invoke(request, response);
        EasyMock.expectLastCall();

        EasyMock.replay(request, response, next);

        valve.invoke(request, response);

        EasyMock.verify(request, response, next);
    }

    @Test
    public void testEmptyParameters() throws IOException, ServletException {
        valve.setMaxGlobalParams(2);

        Map<String, String[]> parameters = new HashMap<>();

        EasyMock.expect(request.getRequestURI()).andReturn("/other/endpoint");
        EasyMock.expect(request.getParameterMap()).andReturn(parameters);
        next.invoke(request, response);
        EasyMock.expectLastCall();

        EasyMock.replay(request, response, next);

        valve.invoke(request, response);

        EasyMock.verify(request, response, next);
    }
}
