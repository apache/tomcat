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
package org.apache.catalina.tribes.test.interceptors;

import java.io.Serializable;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.group.interceptors.DomainFilterInterceptor;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

public class TestDomainInterceptor extends TestCase {

    GroupChannel[] channels = null;
    DomainFilterInterceptor[] domainitcs = null;
    TestListener[] test = null;
    int channelCount = 4;
    Thread[] threads = null;
    byte[] commonDomain = new byte[] {1,1,1,1};
    byte[] oddDomain = new byte[] {2,1,1,1};
    protected void setUp() throws Exception {
        System.out.println("Setup");
        super.setUp();
        channels = new GroupChannel[channelCount];
        domainitcs = new DomainFilterInterceptor[channelCount];
        test = new TestListener[channelCount];
        threads = new Thread[channelCount];
        for ( int i=0; i<channelCount; i++ ) {
            channels[i] = new GroupChannel();
            channels[i].getMembershipService().setDomain(i>0?commonDomain:oddDomain);
            domainitcs[i] = new DomainFilterInterceptor();
            domainitcs[i].setDomain(i>0?commonDomain:oddDomain);
            channels[i].addInterceptor(domainitcs[i]);
            test[i] = new TestListener(i);
            channels[i].addChannelListener(test[i]);
            final int j = i;
            threads[i] = new Thread() {
                public void run() {
                    try {
                        channels[j].start(Channel.DEFAULT);
                        Thread.sleep(50);
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            };
        }
        for ( int i=0; i<channelCount; i++ ) threads[i].start();
        for ( int i=0; i<channelCount; i++ ) threads[i].join();
        Thread.sleep(1000);
    }

    public void testDomainMembership() throws Exception {
        assertEquals("Testing odd channel - no members.",0,channels[0].getMembers().length);
        for (int i=1; i<channelCount; i++ ) {
            assertEquals("["+i+"] Testing common channels - should have " + 
                         (channelCount - 1) + " members.", (channelCount - 2),
                         channels[i].getMembers().length);
        }
    }

    public void testDomainMessages() throws Exception {
    }


    protected void tearDown() throws Exception {
        System.out.println("tearDown");
        super.tearDown();
        for ( int i=0; i<channelCount; i++ ) {
            channels[i].stop(Channel.DEFAULT);
        }
    }

    public static void main(String[] args) throws Exception {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(TestDomainInterceptor.class);
        suite.run(new TestResult());
    }

    public static class TestListener implements ChannelListener {
        int id = -1;
        public TestListener(int id) {
            this.id = id;
        }
        int cnt = 0;
        int total = 0;
        boolean fail = false;
        public synchronized void messageReceived(Serializable msg, Member sender) {
            total++;
            Integer i = (Integer)msg;
            if ( i.intValue() != cnt ) fail = true;
            else cnt++;
            System.out.println("Listener["+id+"] Message received:"+i+" Count:"+total+" Fail:"+fail);

        }

        public boolean accept(Serializable msg, Member sender) {
            return (msg instanceof Integer);
        }
    }






}
