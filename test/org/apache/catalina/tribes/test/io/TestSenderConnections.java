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
package org.apache.catalina.tribes.test.io;

import java.util.ArrayList;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ManagedChannel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.group.GroupChannel;
import junit.framework.TestCase;
import org.apache.catalina.tribes.ChannelListener;
import java.io.Serializable;
import java.util.Random;
import java.util.HashMap;
import org.apache.catalina.tribes.transport.ReplicationTransmitter;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.transport.DataSender;
import org.apache.catalina.tribes.transport.AbstractSender;

public class TestSenderConnections extends TestCase {
    private static int count = 2;
    private ManagedChannel[] channels = new ManagedChannel[count];
    private TestMsgListener[] listeners = new TestMsgListener[count];

    protected void setUp() throws Exception {
        super.setUp();
        for (int i = 0; i < channels.length; i++) {
            channels[i] = new GroupChannel();
            channels[i].getMembershipService().setPayload( ("Channel-" + (i + 1)).getBytes("ASCII"));
            listeners[i] = new TestMsgListener( ("Listener-" + (i + 1)));
            channels[i].addChannelListener(listeners[i]);
            channels[i].start(Channel.SND_RX_SEQ|Channel.SND_TX_SEQ);

        }
    }

    public void clear() {
    }

    public void sendMessages(long delay, long sleep) throws Exception {
        Member local = channels[0].getLocalMember(true);
        Member dest = channels[1].getLocalMember(true);
        int n = 3;
        System.out.println("Sending " + n + " messages from [" + local.getName() + "] to [" + dest.getName() + "]");
        for (int i = 0; i < n; i++) {
            channels[0].send(new Member[] {dest}, new TestMsg(), 0);
            if ( delay > 0 ) Thread.sleep(delay);
        }
        System.out.println("Messages sent. Sleeping for "+(sleep/1000)+" seconds to inspect connections");
        if ( sleep > 0 ) Thread.sleep(sleep);

    }

    public void testConnectionLinger() throws Exception {
        sendMessages(0,15000);
    }
    
    public void testSendToNonExistent() throws Exception {
        ReplicationTransmitter transmitter = (ReplicationTransmitter) channels[0].getChannelSender();
        AbstractSender sender = (AbstractSender)transmitter.getTransport();
        sender.setMaxRetryAttempts(0);
        sender.setTimeout(60000);
        MemberImpl impl = new MemberImpl("127.0.0.1",9443,1000,new byte[]{1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8});
        channels[0].send(new Member[]{impl},new TestMsg(),0);
    }
    

    public void testSendToRemote() throws Exception {
        ReplicationTransmitter transmitter = (ReplicationTransmitter) channels[0].getChannelSender();
        AbstractSender sender = (AbstractSender)transmitter.getTransport();
        sender.setMaxRetryAttempts(0);
        sender.setTimeout(60000);
        MemberImpl impl = new MemberImpl("127.0.0.1",9999,1000,new byte[]{1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8});
        for (int i=0; i<1000; i++) {
            if (i%100==0) System.out.println("Sending message:"+(i+1));
            channels[0].send(new Member[] {impl}, new TestMsg(), 0);
        }
    }


    public void testSendToFailing() throws Exception {
        ReplicationTransmitter transmitter = (ReplicationTransmitter) channels[0].getChannelSender();
        AbstractSender sender = (AbstractSender)transmitter.getTransport();
        sender.setMaxRetryAttempts(0);
        sender.setTimeout(60000);
        Member[] ma = channels[0].getMembers();
        final Member m = channels[1].getLocalMember(true);
        Thread st = new Thread() {
            public void run() {
                try {
                    for (int i=0; i<10000; i++ ) { 
                        channels[0].send(new Member[] {m}, new TestMsg(), 0);
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
        };
        st.start();
        Thread.sleep(250);
        channels[1].stop(Channel.DEFAULT);
        st.join();
    }


    public void testKeepAliveCount() throws Exception {
        System.out.println("Setting keep alive count to 0");
        for (int i = 0; i < channels.length; i++) {
            ReplicationTransmitter t = (ReplicationTransmitter)channels[0].getChannelSender();
            t.getTransport().setKeepAliveCount(0);
        }
        sendMessages(1000,15000);
    }

    public void testKeepAliveTime() throws Exception {
        System.out.println("Setting keep alive count to 1 second");
        for (int i = 0; i < channels.length; i++) {
            ReplicationTransmitter t = (ReplicationTransmitter)channels[0].getChannelSender();
            t.getTransport().setKeepAliveTime(1000);
        }
        sendMessages(2000,15000);
    }

    protected void tearDown() throws Exception {
        for (int i = 0; i < channels.length; i++) {
            channels[i].stop(Channel.DEFAULT);
        }

    }
    
    public static class TestMsg implements Serializable {
        static Random r = new Random(System.currentTimeMillis());
        HashMap map = new HashMap();
        public TestMsg() {
            int size = Math.abs(r.nextInt() % 200);
            for (int i=0; i<size; i++ ) {
                int length = Math.abs(r.nextInt() %65000);
                ArrayList list = new ArrayList(length);
                map.put(new Integer(i),list);
            }
        }
    }

    public class TestMsgListener implements ChannelListener {
        public String name = null;
        public TestMsgListener(String name) {
            this.name = name;
        }
        
        public void messageReceived(Serializable msg, Member sender) {
            System.out.println("["+name+"] Received message:"+msg+" from " + sender.getName());
        }

    
        public boolean accept(Serializable msg, Member sender) {
            return true;
        }


        
    }

}
