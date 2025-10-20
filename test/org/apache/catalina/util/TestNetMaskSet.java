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

package org.apache.catalina.util;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestNetMaskSet {

    @Test
    public void testNetMaskSet() throws UnknownHostException {

        NetMaskSet nms = new NetMaskSet();
        nms.addAll("192.168.0.0/24, 192.168.1.0/27, 192.168.2.2, 10.0.0.0/8");

        Assert.assertTrue(nms.contains("192.168.0.5"));
        Assert.assertTrue(nms.contains("192.168.0.255"));

        Assert.assertTrue(nms.contains("192.168.1.0"));
        Assert.assertTrue(nms.contains("192.168.1.1"));
        Assert.assertTrue(nms.contains("192.168.1.31"));
        Assert.assertFalse(nms.contains("192.168.1.32"));

        Assert.assertTrue(nms.contains("192.168.2.2"));
        Assert.assertFalse(nms.contains("192.168.2.1"));
        Assert.assertFalse(nms.contains("192.168.2.3"));

        Assert.assertTrue(nms.contains("10.10.10.10"));
        Assert.assertTrue(nms.contains("10.20.30.40"));
        Assert.assertFalse(nms.contains("9.10.10.10"));
        Assert.assertFalse(nms.contains("11.10.10.10"));

        String s = nms.toString();
        Assert.assertTrue(s.indexOf('[') == -1);
        Assert.assertTrue(s.indexOf(']') == -1);

        List<String> list = Arrays.asList(s.split("\\s*,\\s*"));
        Assert.assertTrue(list.contains("192.168.0.0/24"));
        Assert.assertTrue(list.contains("192.168.1.0/27"));
        Assert.assertTrue(list.contains("192.168.2.2"));
        Assert.assertTrue(list.contains("10.0.0.0/8"));
    }
}
