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
 */
package org.apache.catalina.tribes.test.channel;

import org.apache.catalina.tribes.group.GroupChannel;
import junit.framework.TestCase;
import org.apache.catalina.tribes.transport.ReceiverBase;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class ChannelStartStop extends TestCase {
    GroupChannel channel = null;
    protected void setUp() throws Exception {
        super.setUp();
        channel = new GroupChannel();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        try {channel.stop(channel.DEFAULT);}catch (Exception ignore){}
    }
    
    public void testDoubleFullStart() throws Exception {
        int count = 0;
        try {
            channel.start(channel.DEFAULT);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(channel.DEFAULT);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        assertEquals(count,2);
        channel.stop(channel.DEFAULT);
    }

    public void testScrap() throws Exception {
        System.out.println(channel.getChannelReceiver().getClass());
        ((ReceiverBase)channel.getChannelReceiver()).setMaxThreads(1);
    } 


    public void testDoublePartialStart() throws Exception {
        //try to double start the RX 
        int count = 0;
        try {
            channel.start(channel.SND_RX_SEQ);
            channel.start(channel.MBR_RX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(channel.MBR_RX_SEQ);
            count++;
        } catch ( Exception x){/*expected*/}
        assertEquals(count,1);
        channel.stop(channel.DEFAULT);
        //double the membership sender
        count = 0;
        try {
            channel.start(channel.SND_RX_SEQ);
            channel.start(channel.MBR_TX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(channel.MBR_TX_SEQ);
            count++;
        } catch ( Exception x){/*expected*/}
        assertEquals(count,1);
        channel.stop(channel.DEFAULT);
        
        count = 0;
        try {
            channel.start(channel.SND_RX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(channel.SND_RX_SEQ);
            count++;
        } catch ( Exception x){/*expected*/}
        assertEquals(count,1);
        channel.stop(channel.DEFAULT);

        count = 0;
        try {
            channel.start(channel.SND_TX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(channel.SND_TX_SEQ);
            count++;
        } catch ( Exception x){/*expected*/}
        assertEquals(count,1);
        channel.stop(channel.DEFAULT);
    }
    
    public void testFalseOption() throws Exception {
        int flag = 0xFFF0;//should get ignored by the underlying components
        int count = 0;
        try {
            channel.start(flag);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(flag);
            count++;
        } catch ( Exception x){/*expected*/}
        assertEquals(count,2);
        channel.stop(channel.DEFAULT);
    }

}
