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
package org.apache.coyote;

import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TestRequest {
    final Request request = new Request();
    final Response response = new Response();
    final ActionHook actionHook = EasyMock.createNiceMock(ActionHook.class);

    @Before
    public void setup() {
        // setup response
        response.setHook(actionHook);
        response.setCommitted(false);

        // setup request
        final ByteChunk body = new ByteChunk();
        final InputBuffer inputBuffer = new SavedRequestInputFilter(body);
        request.setInputBuffer(inputBuffer);

        // connect the request with the response
        request.setResponse(response);
    }

    @Test
    public void test100ContinueExpectationImmediately() throws IOException {
        // tests that response.sendAcknowledgement is only called when
        // request.setContinueHandlingResponsePolicy is called

        request.setExpectation(true);

        // now setup the mock to verify that sendAcknowledgement is called
        configureMockForOneAckowledgementCall(actionHook, response);

        request.setContinueHandlingResponsePolicy(ContinueHandlingResponsePolicy.IMMEDIATELY);

        // verify that sendAcknowledgement is not called
        EasyMock.verify(actionHook);

        // setup the mock to verify that sendAcknowledgement is not called
        configureMockForZeroAckowledgementCalls(actionHook);

        request.doRead(new DoNothingApplicationBufferHandler());

        // verify that sendAcknowledgement is called
        EasyMock.verify(actionHook);
    }

    @Test
    public void test100ContinueExpectationOnRequestBodyRead() throws IOException {
        // tests that response.sendAcknowledgement is only called when
        // request.doRead is called

        request.setExpectation(true);

        // setup the mock to verify that sendAcknowledgement is not called
        configureMockForZeroAckowledgementCalls(actionHook);

        request.setContinueHandlingResponsePolicy(ContinueHandlingResponsePolicy.ON_REQUEST_BODY_READ);

        // verify that sendAcknowledgement is not called
        EasyMock.verify(actionHook);

        // setup the mock to verify that sendAcknowledgement is called
        configureMockForOneAckowledgementCall(actionHook, response);

        request.doRead(new DoNothingApplicationBufferHandler());

        // verify that sendAcknowledgement is called
        EasyMock.verify(actionHook);
    }


    @Test
    public void testNoExpectationWithOnRequestBodyReadPolicy() throws IOException {
        // when expectation is false, sendAcknowledgement must never be called

        request.setExpectation(false);

        // setup the mock to verify that sendAcknowledgement is not called
        configureMockForZeroAckowledgementCalls(actionHook);

        request.setContinueHandlingResponsePolicy(ContinueHandlingResponsePolicy.ON_REQUEST_BODY_READ);
        request.doRead(new DoNothingApplicationBufferHandler());

        // verify that sendAcknowledgement is called
        EasyMock.verify(actionHook);
    }


    @Test
    public void testNoExpectationWithOnRequestImmediately() throws IOException {
        // when expectation is false, sendAcknowledgement must never be called

        request.setExpectation(false);

        // setup the mock to verify that sendAcknowledgement is not called
        configureMockForZeroAckowledgementCalls(actionHook);

        request.setContinueHandlingResponsePolicy(ContinueHandlingResponsePolicy.IMMEDIATELY);
        request.doRead(new DoNothingApplicationBufferHandler());

        // verify that sendAcknowledgement is called
        EasyMock.verify(actionHook);
    }


    private void configureMockForOneAckowledgementCall(ActionHook actionHook, Response response) {
        EasyMock.reset(actionHook);
        actionHook.action(ActionCode.ACK, response);
        EasyMock.expectLastCall().once();
        EasyMock.replay(actionHook);
    }


    private void configureMockForZeroAckowledgementCalls(ActionHook actionHook) {
        EasyMock.reset(actionHook);
        EasyMock.replay(actionHook);
    }


    private class DoNothingApplicationBufferHandler implements ApplicationBufferHandler {
        @Override
        public void setByteBuffer(ByteBuffer buffer) {

        }

        @Override
        public ByteBuffer getByteBuffer() {
            return null;
        }

        @Override
        public void expand(int size) {

        }
    }
}
