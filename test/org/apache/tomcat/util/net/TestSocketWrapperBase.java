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
package org.apache.tomcat.util.net;

import org.junit.Assert;
import org.junit.Test;

import org.easymock.EasyMock;

public class TestSocketWrapperBase {

    @Test
    public void testDuplicateWriteInterestRegistration() {
        SocketWrapperBase<?> socketWrapper = EasyMock.createMockBuilder(SocketWrapperBase.class)
                .withConstructor(Object.class, AbstractEndpoint.class)
                .withArgs(new Object(), EasyMock.createNiceMock(AbstractEndpoint.class))
                .addMockedMethod("doRegisterWriteInterest")
                .createMock();

        socketWrapper.doRegisterWriteInterest();
        EasyMock.expectLastCall().once();
        EasyMock.replay(socketWrapper);

        socketWrapper.registerWriteInterest();
        Assert.assertThrows(IllegalStateException.class, socketWrapper::registerWriteInterest);

        EasyMock.verify(socketWrapper);
    }

    @Test
    public void testWriteInterestRegistrationAfterClear() {
        SocketWrapperBase<?> socketWrapper = EasyMock.createMockBuilder(SocketWrapperBase.class)
                .withConstructor(Object.class, AbstractEndpoint.class)
                .withArgs(new Object(), EasyMock.createNiceMock(AbstractEndpoint.class))
                .addMockedMethod("doRegisterWriteInterest")
                .createMock();

        socketWrapper.doRegisterWriteInterest();
        EasyMock.expectLastCall().times(2);
        EasyMock.replay(socketWrapper);

        socketWrapper.registerWriteInterest();
        socketWrapper.clearWriteInterest();
        socketWrapper.registerWriteInterest();

        EasyMock.verify(socketWrapper);
    }
}
