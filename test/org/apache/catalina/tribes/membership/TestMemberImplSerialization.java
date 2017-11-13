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
package org.apache.catalina.tribes.membership;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.tribes.Member;

public class TestMemberImplSerialization {
    private MemberImpl m1, m2, p1,p2;
    private byte[] payload = null;
    private int udpPort = 3445;

    @Before
    public void setUp() throws Exception {
        payload = new byte[333];
        Arrays.fill(payload,(byte)1);
        m1 = new MemberImpl("localhost",3333,1,payload);
        m2 = new MemberImpl("localhost",3333,1);
        payload = new byte[333];
        Arrays.fill(payload,(byte)2);
        p1 = new MemberImpl("127.0.0.1",3333,1,payload);
        p2 = new MemberImpl("localhost",3331,1,payload);
        m1.setDomain(new byte[] {1,2,3,4,5,6,7,8,9});
        m2.setDomain(new byte[] {1,2,3,4,5,6,7,8,9});
        m1.setCommand(new byte[] {1,2,4,5,6,7,8,9});
        m2.setCommand(new byte[] {1,2,4,5,6,7,8,9});
        m1.setUdpPort(udpPort);
        m2.setUdpPort(m1.getUdpPort());
    }

    @Test
    public void testCompare() throws Exception {
        Assert.assertTrue(m1.equals(m2));
        Assert.assertTrue(m2.equals(m1));
        Assert.assertTrue(p1.equals(m2));
        Assert.assertFalse(m1.equals(p2));
        Assert.assertFalse(m1.equals(p2));
        Assert.assertFalse(m2.equals(p2));
        Assert.assertFalse(p1.equals(p2));
    }

    @Test
    public void testUdpPort() throws Exception {
        byte[] md1 = m1.getData();
        byte[] md2 = m2.getData();

        Member a1 = MemberImpl.getMember(md1);
        Member a2 = MemberImpl.getMember(md2);

        Assert.assertTrue(a1.getUdpPort()==a2.getUdpPort());
        Assert.assertTrue(a1.getUdpPort()==udpPort);
    }

    @Test
    public void testSerializationOne() throws Exception {
        Member m = m1;
        byte[] md1 = m.getData(false,true);
        byte[] mda1 = m.getData(false,false);
        Assert.assertTrue(Arrays.equals(md1,mda1));
        Assert.assertTrue(md1==mda1);
        mda1 = m.getData(true,true);
        Member ma1 = MemberImpl.getMember(mda1);
        Assert.assertTrue(compareMembers(m,ma1));
        mda1 = p1.getData(false);
        Assert.assertFalse(Arrays.equals(md1,mda1));
        ma1 = MemberImpl.getMember(mda1);
        Assert.assertTrue(compareMembers(p1,ma1));

        md1 = m.getData(true,true);
        Thread.sleep(50);
        mda1 = m.getData(true,true);
        Member a1 = MemberImpl.getMember(md1);
        Member a2 = MemberImpl.getMember(mda1);
        Assert.assertTrue(a1.equals(a2));
        Assert.assertFalse(Arrays.equals(md1,mda1));


    }

    public boolean compareMembers(Member impl1, Member impl2) {
        boolean result = true;
        result = result && Arrays.equals(impl1.getHost(),impl2.getHost());
        result = result && Arrays.equals(impl1.getPayload(),impl2.getPayload());
        result = result && Arrays.equals(impl1.getUniqueId(),impl2.getUniqueId());
        result = result && impl1.getPort() == impl2.getPort();
        return result;
    }
}
