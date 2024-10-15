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
package org.apache.catalina.tribes.transport.nio;

import java.io.IOException;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.transport.DataSender;
import org.apache.catalina.tribes.transport.PooledSender;
import org.apache.catalina.tribes.util.StringManager;

public class PooledParallelSender extends PooledSender implements PooledParallelSenderMBean {
    protected static final StringManager sm = StringManager.getManager(PooledParallelSender.class);

    @Override
    public void sendMessage(Member[] destination, ChannelMessage message) throws ChannelException {
        if (!isConnected()) {
            throw new ChannelException(sm.getString("pooledParallelSender.sender.disconnected"));
        }
        ParallelNioSender sender = (ParallelNioSender) getSender();
        if (sender == null) {
            ChannelException cx = new ChannelException(
                    sm.getString("pooledParallelSender.unable.retrieveSender.timeout", Long.toString(getMaxWait())));
            for (Member member : destination) {
                cx.addFaultyMember(member,
                        new NullPointerException(sm.getString("pooledParallelSender.unable.retrieveSender")));
            }
            throw cx;
        } else {
            try {
                if (!sender.isConnected()) {
                    sender.connect();
                }
                sender.sendMessage(destination, message);
                sender.keepalive();
            } catch (ChannelException x) {
                sender.disconnect();
                throw x;
            } finally {
                returnSender(sender);
            }
        }
    }

    @Override
    public DataSender getNewDataSender() {
        try {
            ParallelNioSender sender = new ParallelNioSender();
            transferProperties(this, sender);
            return sender;
        } catch (IOException x) {
            throw new RuntimeException(sm.getString("pooledParallelSender.unable.open"), x);
        }
    }
}