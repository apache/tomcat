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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.servlets.WebdavServlet.BoundedByteArrayOutputStream;

public class TestWebdavBoundedByteArrayOutputStream {

    private static final int TEST_LIMIT = 10;
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0 };


    @Test
    public void testWriteByte() {
        BoundedByteArrayOutputStream bbaos = new BoundedByteArrayOutputStream(TEST_LIMIT);

        for (int i = 0; i < TEST_LIMIT; i++) {
            bbaos.write(0);
        }

        try {
            bbaos.write(0);
            Assert.fail("Writing 11th byte failed to trigger error");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Pass
        }
    }


    @Test
    public void testWriteByteArray() throws IOException {
        BoundedByteArrayOutputStream bbaos = new BoundedByteArrayOutputStream(TEST_LIMIT);

        for (int i = 0; i < TEST_LIMIT; i++) {
            bbaos.write(ONE_BYTE_ARRAY);
        }

        try {
            bbaos.write(ONE_BYTE_ARRAY);
            Assert.fail("Writing 11th byte failed to trigger error");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Pass
        }
    }


    @Test
    public void testWriteByteSubArray() {
        BoundedByteArrayOutputStream bbaos = new BoundedByteArrayOutputStream(TEST_LIMIT);

        for (int i = 0; i < TEST_LIMIT; i++) {
            bbaos.write(ONE_BYTE_ARRAY, 0, 1);
        }

        try {
            bbaos.write(ONE_BYTE_ARRAY, 0, 1);
            Assert.fail("Writing 11th byte failed to trigger error");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Pass
        }
    }


    @Test
    public void testWriteBytes() {
        BoundedByteArrayOutputStream bbaos = new BoundedByteArrayOutputStream(TEST_LIMIT);

        for (int i = 0; i < TEST_LIMIT; i++) {
            bbaos.writeBytes(ONE_BYTE_ARRAY);
        }

        try {
            bbaos.writeBytes(ONE_BYTE_ARRAY);
            Assert.fail("Writing 11th byte failed to trigger error");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Pass
        }
    }
}
