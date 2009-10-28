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

import org.apache.catalina.tribes.test.channel.TestChannelStartStop;
import org.apache.catalina.tribes.test.channel.TestChannelOptionFlag;
import org.apache.catalina.tribes.test.channel.TestDataIntegrity;
import org.apache.catalina.tribes.test.channel.TestMulticastPackages;
import org.apache.catalina.tribes.test.channel.TestRemoteProcessException;
import org.apache.catalina.tribes.test.channel.TestUdpPackages;
import org.apache.catalina.tribes.test.interceptors.TestNonBlockingCoordinator;
import org.apache.catalina.tribes.test.interceptors.TestOrderInterceptor;
import org.apache.catalina.tribes.test.io.TestSenderConnections;
import org.apache.catalina.tribes.test.io.TestSerialization;
import org.apache.catalina.tribes.test.membership.TestMemberSerialization;
import org.apache.catalina.tribes.test.membership.TestDomainFilter;
import org.apache.catalina.tribes.test.membership.TestMemberArrival;
import org.apache.catalina.tribes.test.membership.TestTcpFailureDetector;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TribesTestSuite
    extends TestCase {

    public TribesTestSuite(String s) {
        super(s);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        // o.a.catalina.tribes.test.channel
        suite.addTestSuite(TestChannelStartStop.class);
        suite.addTestSuite(TestChannelOptionFlag.class);
        suite.addTestSuite(TestDataIntegrity.class);
        suite.addTestSuite(TestMulticastPackages.class);
        suite.addTestSuite(TestRemoteProcessException.class);
        suite.addTestSuite(TestUdpPackages.class);
        // o.a.catalina.tribes.test.interceptors
        suite.addTestSuite(TestNonBlockingCoordinator.class);
        suite.addTestSuite(TestOrderInterceptor.class);
        // o.a.catalina.tribes.test.io
        suite.addTestSuite(TestSenderConnections.class);
        suite.addTestSuite(TestSerialization.class);
        // o.a.catalina.tribes.test.membership
        suite.addTestSuite(TestMemberSerialization.class);
        suite.addTestSuite(TestDomainFilter.class);
        suite.addTestSuite(TestMemberArrival.class);
        suite.addTestSuite(TestTcpFailureDetector.class);
        return suite;
    }
}
