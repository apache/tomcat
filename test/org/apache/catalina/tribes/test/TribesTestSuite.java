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
package org.apache.catalina.tribes.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.catalina.tribes.group.TestGroupChannelMemberArrival;
import org.apache.catalina.tribes.group.TestGroupChannelOptionFlag;
import org.apache.catalina.tribes.group.TestGroupChannelSenderConnections;
import org.apache.catalina.tribes.group.TestGroupChannelStartStop;
import org.apache.catalina.tribes.group.interceptors.TestDomainFilterInterceptor;
import org.apache.catalina.tribes.group.interceptors.TestNonBlockingCoordinator;
import org.apache.catalina.tribes.group.interceptors.TestOrderInterceptor;
import org.apache.catalina.tribes.group.interceptors.TestTcpFailureDetector;
import org.apache.catalina.tribes.io.TestXByteBuffer;
import org.apache.catalina.tribes.membership.TestMemberImplSerialization;
import org.apache.catalina.tribes.test.channel.TestDataIntegrity;
import org.apache.catalina.tribes.test.channel.TestMulticastPackages;
import org.apache.catalina.tribes.test.channel.TestRemoteProcessException;
import org.apache.catalina.tribes.test.channel.TestUdpPackages;

public class TribesTestSuite
    extends TestCase {

    public TribesTestSuite(String s) {
        super(s);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        // o.a.catalina.tribes.test.channel
        suite.addTestSuite(TestGroupChannelStartStop.class);
        suite.addTestSuite(TestGroupChannelOptionFlag.class);
        suite.addTestSuite(TestDataIntegrity.class);
        suite.addTestSuite(TestMulticastPackages.class);
        suite.addTestSuite(TestRemoteProcessException.class);
        suite.addTestSuite(TestUdpPackages.class);
        // o.a.catalina.tribes.test.interceptors
        suite.addTestSuite(TestNonBlockingCoordinator.class);
        suite.addTestSuite(TestOrderInterceptor.class);
        // o.a.catalina.tribes.test.io
        suite.addTestSuite(TestGroupChannelSenderConnections.class);
        suite.addTestSuite(TestXByteBuffer.class);
        // o.a.catalina.tribes.test.membership
        suite.addTestSuite(TestMemberImplSerialization.class);
        suite.addTestSuite(TestDomainFilterInterceptor.class);
        suite.addTestSuite(TestGroupChannelMemberArrival.class);
        suite.addTestSuite(TestTcpFailureDetector.class);
        return suite;
    }
}
