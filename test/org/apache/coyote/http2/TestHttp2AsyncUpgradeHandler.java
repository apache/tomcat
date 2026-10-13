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
package org.apache.coyote.http2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

public class TestHttp2AsyncUpgradeHandler {

    /*
     * AsyncHeaderFrameBuffers is a private, static nested class so reflection is used to test that payload expansion is
     * capped at the maximum frame size that can be represented in a frame header. Previously the payload size was
     * simply doubled on every expansion which, for very large header blocks, eventually overflowed the size resulting
     * in an attempt to allocate a buffer with a negative capacity.
     */
    @Test
    public void testPayloadExpansionIsCapped() throws Exception {
        Class<?> clazz = Class.forName("org.apache.coyote.http2.Http2AsyncUpgradeHandler$AsyncHeaderFrameBuffers");
        Constructor<?> constructor = clazz.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        Object buffers = constructor.newInstance(Integer.valueOf(1024));
        Method expandPayload = clazz.getDeclaredMethod("expandPayload");
        expandPayload.setAccessible(true);

        int expectedSize = 1024;
        while (expectedSize < ConnectionSettingsBase.MAX_MAX_FRAME_SIZE) {
            expandPayload.invoke(buffers);
            expectedSize = Integer.min(expectedSize * 2, ConnectionSettingsBase.MAX_MAX_FRAME_SIZE);
            Assert.assertEquals(expectedSize, readPayloadSize(clazz, buffers));
        }

        // A buffer that is already as large as the largest permitted frame
        // can not be expanded further
        try {
            expandPayload.invoke(buffers);
            Assert.fail("Expansion beyond the maximum permitted frame size was expected to fail.");
        } catch (InvocationTargetException e) {
            Assert.assertEquals(IllegalStateException.class, e.getCause().getClass());
        }
    }


    private int readPayloadSize(Class<?> clazz, Object buffers) throws Exception {
        Field field = clazz.getDeclaredField("payloadSize");
        field.setAccessible(true);
        return ((Integer) field.get(buffers)).intValue();
    }
}
