package org.apache.catalina.tribes.test;

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
        suite.addTestSuite(org.apache.catalina.tribes.test.channel.ChannelStartStop.class);
        suite.addTestSuite(org.apache.catalina.tribes.test.channel.TestChannelOptionFlag.class);
        suite.addTestSuite(org.apache.catalina.tribes.test.membership.MemberSerialization.class);
        suite.addTestSuite(org.apache.catalina.tribes.test.membership.TestMemberArrival.class);
        suite.addTestSuite(org.apache.catalina.tribes.test.membership.TestTcpFailureDetector.class);
        suite.addTestSuite(org.apache.catalina.tribes.test.channel.TestDataIntegrity.class);
        return suite;
    }
}
