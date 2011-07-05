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
package org.apache.catalina.tribes.group.interceptors;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.GroupChannel;

public class TestNonBlockingCoordinator extends TestCase {

    GroupChannel[] channels = null;
    NonBlockingCoordinator[] coordinators = null;
    int channelCount = 10;
    Thread[] threads = null;
    @Override
    protected void setUp() throws Exception {
        System.out.println("Setup");
        super.setUp();
        channels = new GroupChannel[channelCount];
        coordinators = new NonBlockingCoordinator[channelCount];
        threads = new Thread[channelCount];
        for ( int i=0; i<channelCount; i++ ) {
            channels[i] = new GroupChannel();
            coordinators[i] = new NonBlockingCoordinator();
            channels[i].addInterceptor(coordinators[i]);
            channels[i].addInterceptor(new TcpFailureDetector());
            final int j = i;
            threads[i] = new Thread() {
                @Override
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
    
    public void testCoord1() throws Exception {
        for (int i=1; i<channelCount; i++ ) 
            assertEquals("Message count expected to be equal.",channels[i-1].getMembers().length,channels[i].getMembers().length);
        Member member = coordinators[0].getCoordinator();
        int cnt = 0;
        while ( member == null && (cnt++ < 100 ) ) try {Thread.sleep(100); member = coordinators[0].getCoordinator();}catch ( Exception x){ /* Ignore */ }
        for (int i=0; i<channelCount; i++ ) assertEquals(member,coordinators[i].getCoordinator());
        System.out.println("Coordinator[1] is:"+member);
        
    }
    
    public void testCoord2() throws Exception {
        Member member = coordinators[1].getCoordinator();
        System.out.println("Coordinator[2a] is:" + member);
        int index = -1;
        for ( int i=0; i<channelCount; i++ ) {
            if ( channels[i].getLocalMember(false).equals(member) ) {
                System.out.println("Shutting down:" + channels[i].getLocalMember(true).toString());
                channels[i].stop(Channel.DEFAULT);
                index = i;
            }
        }
        int dead = index;
        Thread.sleep(1000);
        if ( index == 0 ) index = 1; else index = 0;
        System.out.println("Member count:"+channels[index].getMembers().length);
        member = coordinators[index].getCoordinator();
        for (int i = 1; i < channelCount; i++) if ( i != dead ) assertEquals(member, coordinators[i].getCoordinator());
        System.out.println("Coordinator[2b] is:" + member);
    }

    @Override
    protected void tearDown() throws Exception {
        System.out.println("tearDown");
        super.tearDown();
        for ( int i=0; i<channelCount; i++ ) {
            channels[i].stop(Channel.DEFAULT);
        }
    }
    
    public static void main(String[] args) throws Exception {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(TestNonBlockingCoordinator.class);
        suite.run(new TestResult());
    }


}
