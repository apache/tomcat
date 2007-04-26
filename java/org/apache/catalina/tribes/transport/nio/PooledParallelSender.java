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
import org.apache.catalina.tribes.transport.MultiPointSender;
import org.apache.catalina.tribes.transport.PooledSender;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class PooledParallelSender extends PooledSender implements MultiPointSender {
    protected boolean connected = true;
    public PooledParallelSender() {
        super();
    }
    
    public void sendMessage(Member[] destination, ChannelMessage message) throws ChannelException {
        if ( !connected ) throw new ChannelException("Sender not connected.");
        ParallelNioSender sender = (ParallelNioSender)getSender();
        try {
            sender.sendMessage(destination, message);
            sender.keepalive();
        }finally {
            if ( !connected ) disconnect();
            returnSender(sender);
        }
    }

    public DataSender getNewDataSender() {
        try {
            ParallelNioSender sender = new ParallelNioSender();
            sender.transferProperties(this,sender);
            return sender;
        } catch ( IOException x ) {
            throw new RuntimeException("Unable to open NIO selector.",x);
        }
    }
    
    public synchronized void disconnect() {
        this.connected = false;
        super.disconnect();
    }

    public synchronized void connect() throws IOException {
        this.connected = true;
        super.connect();
    }
   
}