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
package org.apache.catalina.tribes.transport;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.tribes.group.TribesSslContext;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.transport.nio.NioSender;
import org.apache.catalina.tribes.transport.nio.PooledParallelSender;

public class TestAbstractSender {

    private TribesSslContext sslContext;

    @Before
    public void setUp() {
        try {
            sslContext = new TribesSslContext("tribes-test",
                    "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", "SHA256", "TLSv1.3");
        } catch (Exception e) {
            Assume.assumeNoException(e);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (sslContext != null) {
            sslContext.close();
        }
    }

    @Test
    public void testTransferProperties() throws Exception {
        AbstractSender source = new NioSender();
        setValues(source);
        AbstractSender target = new NioSender();
        AbstractSender.transferProperties(source, target);
        assertTransferred(source, target);
    }

    /*
     * A pooled sender hands its own configuration over to every sender it creates, so a configured value has to survive
     * that hand-over. The UDP buffer sizes are only used once a sender connects, which makes them easy to overlook. The
     * new sender is never connected and its selector is released by the cleaner registered by ParallelNioSender.
     */
    @Test
    public void testTransferPropertiesForPooledSender() throws Exception {
        PooledParallelSender pool = new PooledParallelSender();
        setValues(pool);
        assertTransferred(pool, (AbstractSender) pool.getNewDataSender());
    }

    private void setValues(AbstractSender sender) throws Exception {
        sender.setDestination(new MemberImpl("localhost", 4444, 4000));
        sender.setRxBufSize(11111);
        sender.setTxBufSize(22222);
        sender.setUdpRxBufSize(33333);
        sender.setUdpTxBufSize(44444);
        sender.setDirectBuffer(true);
        sender.setKeepAliveCount(55);
        sender.setKeepAliveTime(66666);
        sender.setTimeout(77777);
        sender.setMaxRetryAttempts(3);
        sender.setTcpNoDelay(false);
        sender.setSoKeepAlive(true);
        sender.setOoBInline(false);
        sender.setSoReuseAddress(false);
        sender.setSoLingerOn(true);
        sender.setSoLingerTime(9);
        sender.setSoTrafficClass(0x10);
        sender.setThrowOnFailedAck(false);
        sender.setUdpBased(true);
        sender.setUdpPort(5000);
        sender.setSslContext(sslContext);
    }

    private void assertTransferred(AbstractSender expected, AbstractSender actual) {
        Assert.assertEquals("destination", expected.getDestination(), actual.getDestination());
        Assert.assertEquals("address", expected.getAddress(), actual.getAddress());
        Assert.assertEquals("port", expected.getPort(), actual.getPort());
        Assert.assertEquals("udpPort", expected.getUdpPort(), actual.getUdpPort());
        Assert.assertEquals("rxBufSize", expected.getRxBufSize(), actual.getRxBufSize());
        Assert.assertEquals("txBufSize", expected.getTxBufSize(), actual.getTxBufSize());
        Assert.assertEquals("udpRxBufSize", expected.getUdpRxBufSize(), actual.getUdpRxBufSize());
        Assert.assertEquals("udpTxBufSize", expected.getUdpTxBufSize(), actual.getUdpTxBufSize());
        Assert.assertEquals("directBuffer", expected.getDirectBuffer(), actual.getDirectBuffer());
        Assert.assertEquals("keepAliveCount", expected.getKeepAliveCount(), actual.getKeepAliveCount());
        Assert.assertEquals("keepAliveTime", expected.getKeepAliveTime(), actual.getKeepAliveTime());
        Assert.assertEquals("timeout", expected.getTimeout(), actual.getTimeout());
        Assert.assertEquals("maxRetryAttempts", expected.getMaxRetryAttempts(), actual.getMaxRetryAttempts());
        Assert.assertEquals("tcpNoDelay", expected.getTcpNoDelay(), actual.getTcpNoDelay());
        Assert.assertEquals("soKeepAlive", expected.getSoKeepAlive(), actual.getSoKeepAlive());
        Assert.assertEquals("ooBInline", expected.getOoBInline(), actual.getOoBInline());
        Assert.assertEquals("soReuseAddress", expected.getSoReuseAddress(), actual.getSoReuseAddress());
        Assert.assertEquals("soLingerOn", expected.getSoLingerOn(), actual.getSoLingerOn());
        Assert.assertEquals("soLingerTime", expected.getSoLingerTime(), actual.getSoLingerTime());
        Assert.assertEquals("soTrafficClass", expected.getSoTrafficClass(), actual.getSoTrafficClass());
        Assert.assertEquals("throwOnFailedAck", expected.getThrowOnFailedAck(), actual.getThrowOnFailedAck());
        Assert.assertEquals("udpBased", expected.isUdpBased(), actual.isUdpBased());
        Assert.assertEquals("sslContext", expected.getSslContext(), actual.getSslContext());
    }
}
