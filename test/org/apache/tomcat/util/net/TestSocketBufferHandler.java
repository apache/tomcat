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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;


@RunWith(Parameterized.class)
public class TestSocketBufferHandler {

    @Parameterized.Parameters(name = "{index}: direct[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { Boolean.FALSE });
        parameterSets.add(new Object[] { Boolean.TRUE });

        return parameterSets;
    }


    @Parameter(0)
    public boolean direct;


    @Test
    public void testReturnWhenEmpty() {
        SocketBufferHandler sbh = new SocketBufferHandler(8, 8, direct);
        sbh.unReadReadBuffer(ByteBuffer.wrap(getBytes("WXYZ")));

        validate(sbh, "WXYZ");
    }


    @Test
    public void testReturnWhenWriteable() {
        SocketBufferHandler sbh = new SocketBufferHandler(8, 8, direct);

        sbh.configureReadBufferForWrite();
        sbh.getReadBuffer().put(getBytes("AB"));

        sbh.unReadReadBuffer(ByteBuffer.wrap(getBytes("WXYZ")));

        validate(sbh, "WXYZAB");
    }


    @Test(expected = BufferOverflowException.class)
    public void testReturnWhenWriteableAndFull() {
        SocketBufferHandler sbh = new SocketBufferHandler(8, 8, direct);

        sbh.configureReadBufferForWrite();
        sbh.getReadBuffer().put(getBytes("ABCDEFGH"));

        sbh.unReadReadBuffer(ByteBuffer.wrap(getBytes("WXYZ")));
    }


    @Test
    public void testReturnWhenReadableAndUnread() {
        SocketBufferHandler sbh = new SocketBufferHandler(8, 8, direct);

        sbh.configureReadBufferForWrite();
        sbh.getReadBuffer().put(getBytes("AB"));
        sbh.configureReadBufferForRead();

        sbh.unReadReadBuffer(ByteBuffer.wrap(getBytes("WXYZ")));

        validate(sbh, "WXYZAB");
    }


    @Test(expected = BufferOverflowException.class)
    public void testReturnWhenReadableAndUnreadAndFull() {
        SocketBufferHandler sbh = new SocketBufferHandler(8, 8, direct);

        sbh.configureReadBufferForWrite();
        sbh.getReadBuffer().put(getBytes("ABCDEF"));
        sbh.configureReadBufferForRead();

        sbh.unReadReadBuffer(ByteBuffer.wrap(getBytes("WXYZ")));
    }


    @Test
    public void testReturnWhenReadableAndPartiallyead() {
        SocketBufferHandler sbh = new SocketBufferHandler(8, 8, direct);

        sbh.configureReadBufferForWrite();
        sbh.getReadBuffer().put(getBytes("ABCDEFGH"));
        sbh.configureReadBufferForRead();
        for (int i = 0; i < 4; i++) {
            sbh.getReadBuffer().get();
        }

        sbh.unReadReadBuffer(ByteBuffer.wrap(getBytes("WXYZ")));

        validate(sbh, "WXYZEFGH");
    }


    private void validate(SocketBufferHandler sbh, String expected) {
        sbh.configureReadBufferForRead();
        for (byte b : getBytes(expected)) {
            Assert.assertEquals(b, sbh.getReadBuffer().get());
        }
        Assert.assertEquals(0,  sbh.getReadBuffer().remaining());
    }


    private byte[] getBytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }
}
