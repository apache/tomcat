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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import org.apache.coyote.http11.Constants;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.coyote.http11.Http11Processor;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.net.SocketBufferHandler;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.easymock.EasyMock;

public class TestRequest {

    private final Http11NioProtocol protocol = new Http11NioProtocol();
    private final Http11Processor processor = new Http11Processor(protocol, null);
    private final Request request = processor.getRequest();
    private final Response response = request.getResponse();
    private final SocketWrapperBase<?> socketWrapper = EasyMock.createNiceMock(SocketWrapperBase.class);


    @Before
    public void setupTest() {
        // Set up the socket wrapper
        EasyMock.expect(socketWrapper.getSocketBufferHandler()).andReturn(new SocketBufferHandler(0, 0, false));
        EasyMock.replay(socketWrapper);
        // Cast to make the method visible
        ((AbstractProcessor) processor).setSocketWrapper(socketWrapper);
    }


    @Test
    public void test100ContinueExpectationImmediately() throws IOException {
        // Tests that response.sendAcknowledgement is only called when
        // request.setContinueHandlingResponsePolicy is called.

        request.setExpectation(true);

        // Configure the mock to verify that a network write is made.
        configureMockForOneAcknowledgementWrite(socketWrapper);

        protocol.setContinueResponseTiming(ContinueResponseTiming.IMMEDIATELY.toString());
        response.action(ActionCode.ACK, ContinueResponseTiming.IMMEDIATELY);

        // Verify that acknowledgement is written to network.
        EasyMock.verify(socketWrapper);

        // Configure the mock to verify that no network write is made.
        configureMockForNoAcknowledgementWrite(socketWrapper);

        request.doRead(new DoNothingApplicationBufferHandler());

        // Verify that acknowledgement is not written to network.
        EasyMock.verify(socketWrapper);
    }


    @Test
    public void test100ContinueExpectationOnRequestBodyRead() throws IOException {
        // Tests that response.sendAcknowledgement is only called when
        // request.doRead is called.

        request.setExpectation(true);

        // Configure the mock to verify that no network write is made.
        configureMockForNoAcknowledgementWrite(socketWrapper);

        protocol.setContinueResponseTiming(ContinueResponseTiming.ON_REQUEST_BODY_READ.toString());
        response.action(ActionCode.ACK, ContinueResponseTiming.IMMEDIATELY);

        // Verify that no acknowledgement is written to network.
        EasyMock.verify(socketWrapper);

        // Configure the mock to verify that a network write is made.
        configureMockForOneAcknowledgementWrite(socketWrapper);

        request.doRead(new DoNothingApplicationBufferHandler());

        // Verify that acknowledgement is written to network.
        EasyMock.verify(socketWrapper);
    }


    @Test
    public void testNoExpectationWithOnRequestBodyReadPolicy() throws IOException {
        // When expectation is false, sendAcknowledgement must never be called.

        request.setExpectation(false);

        // Configure the mock to verify that no network write is made.
        configureMockForNoAcknowledgementWrite(socketWrapper);

        protocol.setContinueResponseTiming(ContinueResponseTiming.ON_REQUEST_BODY_READ.toString());
        request.doRead(new DoNothingApplicationBufferHandler());

        // Verify that no acknowledgement is written to network.
        EasyMock.verify(socketWrapper);
    }


    @Test
    public void testNoExpectationWithOnRequestImmediately() {
        // When expectation is false, sendAcknowledgement must never be called.

        request.setExpectation(false);

        // Configure the mock to verify that no network write is made.
        configureMockForNoAcknowledgementWrite(socketWrapper);

        protocol.setContinueResponseTiming(ContinueResponseTiming.IMMEDIATELY.toString());
        response.action(ActionCode.ACK, ContinueResponseTiming.IMMEDIATELY);

        // Verify that no acknowledgement is written to network.
        EasyMock.verify(socketWrapper);
    }


    private static class DoNothingApplicationBufferHandler implements ApplicationBufferHandler {
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


    private void configureMockForOneAcknowledgementWrite(SocketWrapperBase<?> socketWrapper) throws IOException {
        EasyMock.reset(socketWrapper);
        socketWrapper.write(true, Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length);
        EasyMock.expectLastCall().once();
        EasyMock.replay(socketWrapper);
    }


    private void configureMockForNoAcknowledgementWrite(SocketWrapperBase<?> socketWrapper) {
        EasyMock.reset(socketWrapper);
        EasyMock.replay(socketWrapper);
    }
}
