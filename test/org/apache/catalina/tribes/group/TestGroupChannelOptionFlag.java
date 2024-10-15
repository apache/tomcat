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
package org.apache.catalina.tribes.group;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.tomcat.util.res.StringManager;

public class TestGroupChannelOptionFlag {
    private final StringManager sm = StringManager.getManager(TestGroupChannelOptionFlag.class);
    private GroupChannel channel = null;

    @Before
    public void setUp() throws Exception {
        channel = new GroupChannel();
    }

    @After
    public void tearDown() throws Exception {
        if (channel != null) {
            try {
                channel.stop(Channel.DEFAULT);
            } catch (Exception ignore) {
                // Ignore
            }
        }
        channel = null;
    }

    @Test
    public void testOptionConflict() throws Exception {
        String errorMsgRegx = getTestOptionErrorMsgRegx();

        boolean error = false;
        channel.setOptionCheck(true);
        ChannelInterceptor i = new TestInterceptor();
        i.setOptionFlag(128);
        channel.addInterceptor(i);
        i = new TestInterceptor();
        i.setOptionFlag(128);
        channel.addInterceptor(i);
        try {
            channel.start(Channel.DEFAULT);
        } catch (ChannelException x) {
            if (x.getMessage().matches(errorMsgRegx)) {
                error = true;
            }
        }
        Assert.assertTrue(error);
    }

    @Test
    public void testOptionNoConflict() throws Exception {
        String errorMsgRegx = getTestOptionErrorMsgRegx();

        boolean error = false;
        channel.setOptionCheck(true);
        ChannelInterceptor i = new TestInterceptor();
        i.setOptionFlag(128);
        channel.addInterceptor(i);
        i = new TestInterceptor();
        i.setOptionFlag(64);
        channel.addInterceptor(i);
        i = new TestInterceptor();
        i.setOptionFlag(256);
        channel.addInterceptor(i);
        try {
            channel.start(Channel.DEFAULT);
        } catch (ChannelException x) {
            if (x.getMessage().matches(errorMsgRegx)) {
                error = true;
            }
        }
        Assert.assertFalse(error);
    }

    private String getTestOptionErrorMsgRegx() {
        String errorMsgRegx = sm.getString("groupChannel.optionFlag.conflict", ".+").replace("[", "\\[");
        errorMsgRegx += "; No faulty members identified.";
        return errorMsgRegx;
    }

    public static class TestInterceptor extends ChannelInterceptorBase {
        // Just use base class
    }


}
