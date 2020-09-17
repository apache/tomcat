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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.TesterUtil;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class TestNonBlockingCoordinator {

    private static final Log log = LogFactory.getLog(TestNonBlockingCoordinator.class);

    private static final int CHANNEL_COUNT = 10;

    private GroupChannel[] channels = null;
    private NonBlockingCoordinator[] coordinators = null;

    @Before
    public void setUp() throws Exception {
        log.info("Setup");
        channels = new GroupChannel[CHANNEL_COUNT];
        coordinators = new NonBlockingCoordinator[CHANNEL_COUNT];
        Thread[] threads = new Thread[CHANNEL_COUNT];
        for ( int i=0; i<CHANNEL_COUNT; i++ ) {
            channels[i] = new GroupChannel();
            coordinators[i] = new NonBlockingCoordinator();
            channels[i].addInterceptor(coordinators[i]);
            TcpFailureDetector tcpFailureDetector = new TcpFailureDetector();
            // Double default timeout - mainly for loaded CI systems
            tcpFailureDetector.setReadTestTimeout(tcpFailureDetector.getReadTestTimeout() * 2);
            channels[i].addInterceptor(tcpFailureDetector);
            final int j = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        channels[j].start(Channel.DEFAULT);
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            };
        }
        TesterUtil.addRandomDomain(channels);
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            threads[i].start();
        }
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            threads[i].join();
        }

        // Allow up to 30s for cluster to form once all the nodes have been
        // started
        int count = 0;
        Member member = null;
        boolean electionComplete = false;
        while (!electionComplete && count < 300) {
            electionComplete = true;
            member = coordinators[0].getCoordinator();
            if (member == null) {
                electionComplete = false;
            } else {
                for (int i = 0; i < CHANNEL_COUNT; i++) {
                    electionComplete = electionComplete && (member.equals(coordinators[i].getCoordinator()));
                }
            }
            Thread.sleep(100);
            count++;
        }
    }


    @Test
    public void testCoord1() throws Exception {
        int expectedCount = channels[0].getMembers().length;
        log.info("Expecting each channel to have [" + expectedCount + "] members");
        for (int i = 1; i < CHANNEL_COUNT; i++) {
            Assert.assertEquals("Member count expected to be equal.", expectedCount,
                    channels[i].getMembers().length);
        }

        Member member = coordinators[0].getCoordinator();
        log.info("Coordinator[0] is:" + member);
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            Assert.assertEquals("Local member " + channels[i].getLocalMember(false), member, coordinators[i].getCoordinator());
        }
    }


    @Test
    public void testCoord2() throws Exception {
        // Stop current coordinator to trigger new election
        Member member = coordinators[1].getCoordinator();
        System.out.println("Coordinator[2a] is:" + member);
        int index = -1;
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            if (channels[i].getLocalMember(false).equals(member)) {
                System.out.println("Shutting down:" + channels[i].getLocalMember(true).toString());
                channels[i].stop(Channel.DEFAULT);
                index = i;
                break;
            }
        }

        int dead = index;
        Thread.sleep(1000);
        if (index == 0) {
            index = 1;
        } else {
            index = 0;
        }

        // Allow up to 30s for election to complete
        int count = 0;
        member = null;
        boolean electionComplete = false;
        while (!electionComplete && count < 300) {
            electionComplete = true;
            member = coordinators[index].getCoordinator();
            if (member == null) {
                electionComplete = false;
            } else {
                for (int i = 0; i < CHANNEL_COUNT; i++) {
                    if (i != dead) {
                        electionComplete = electionComplete && (member.equals(coordinators[i].getCoordinator()));
                    }
                }
            }
            Thread.sleep(100);
            count++;
        }

        System.out.println("Member count:"+channels[index].getMembers().length);
        member = coordinators[index].getCoordinator();
        for (int i = 1; i < CHANNEL_COUNT; i++) {
            if (i != dead) {
                Assert.assertEquals(member, coordinators[i].getCoordinator());
            }
        }
        System.out.println("Coordinator[2b] is:" + member);
    }

    @After
    public void tearDown() throws Exception {
        System.out.println("tearDown");
        for ( int i=0; i<CHANNEL_COUNT; i++ ) {
            channels[i].stop(Channel.DEFAULT);
        }
    }

}
