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
package org.apache.catalina.tribes.test.channel;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.ha.session.BackupManager;
import org.apache.catalina.ha.tcp.SimpleTcpCluster;
import org.apache.catalina.tribes.Channel;


public class TestChannelConfig {

    @Test
    public void testIntInput() {

        SimpleTcpCluster cluster = new SimpleTcpCluster();
        cluster.setChannelSendOptions(Channel.SEND_OPTIONS_ASYNCHRONOUS | Channel.SEND_OPTIONS_MULTICAST);
        Assert.assertEquals(Channel.SEND_OPTIONS_ASYNCHRONOUS | Channel.SEND_OPTIONS_MULTICAST, cluster.getChannelSendOptions());
    }

    @Test
    public void testStringInputSimple() {

        SimpleTcpCluster cluster = new SimpleTcpCluster();
        cluster.setChannelSendOptions("multicast");
        Assert.assertEquals(Channel.SEND_OPTIONS_MULTICAST, cluster.getChannelSendOptions());
    }

    @Test
    public void testStringInputCompound() {

        SimpleTcpCluster cluster = new SimpleTcpCluster();
        cluster.setChannelSendOptions("async, multicast");
        Assert.assertEquals(Channel.SEND_OPTIONS_ASYNCHRONOUS | Channel.SEND_OPTIONS_MULTICAST, cluster.getChannelSendOptions());
    }

    @Test
    public void testStringRepresentationOfIntValue() {

        String options = "multicast, async";
        SimpleTcpCluster cluster = new SimpleTcpCluster();
        cluster.setChannelSendOptions(options);
        Assert.assertEquals(options, cluster.getChannelSendOptionsName());
    }

    @Test
    public void testStringInputForMapSendOptions() {

        BackupManager manager = new BackupManager();
        manager.setMapSendOptions("async, multicast");
        Assert.assertEquals(Channel.SEND_OPTIONS_ASYNCHRONOUS | Channel.SEND_OPTIONS_MULTICAST, manager.getMapSendOptions());
    }

}