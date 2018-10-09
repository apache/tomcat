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
package org.apache.catalina.tribes.membership.cloud;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.membership.MemberImpl;

public class TestKubernetesJson extends KubernetesMembershipProvider {

    private static final String JSON_POD_LIST = "{\n" +
            "  \"kind\": \"List\",\n" +
            "  \"apiVersion\": \"v1\",\n" +
            "  \"items\": [\n" +
            "    {\n" +
            "      \"kind\": \"Pod\",\n" +
            "      \"apiVersion\": \"v1\",\n" +
            "      \"metadata\": {\n" +
            "        \"name\": \"test_pod\",\n" +
            "        \"namespace\": \"default\",\n" +
            "        \"selfLink\": \"/api/v1/pods/foo\",\n" +
            "        \"uid\": \"748932794874923\",\n" +
            "        \"resourceVersion\": \"23\",\n" +
            "        \"creationTimestamp\": \"2018-10-02T09:14:01Z\"\n" +
            "      },\n" +
            "      \"status\": {\n" +
            "        \"phase\": \"Running\",\n" +
            "        \"podIP\": \"192.168.0.2\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"kind\": \"Pod\",\n" +
            "      \"apiVersion\": \"v1\",\n" +
            "      \"metadata\": {\n" +
            "        \"name\": \"test_pod_2\",\n" +
            "        \"namespace\": \"default\",\n" +
            "        \"selfLink\": \"/api/v1/pods/foo2\",\n" +
            "        \"uid\": \"7489327944322341414923\",\n" +
            "        \"resourceVersion\": \"18\",\n" +
            "        \"creationTimestamp\": \"2018-10-01T09:14:01Z\"\n" +
            "      },\n" +
            "      \"status\": {\n" +
            "        \"phase\": \"Running\",\n" +
            "        \"podIP\": \"192.168.0.3\"\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    @Test
    public void testJson() throws Exception {
        startTime = Instant.now();

        List<MemberImpl> members = new ArrayList<>();
        parsePods(new StringReader(JSON_POD_LIST), members);

        Assert.assertTrue(members.size() == 2);
        Assert.assertTrue("192.168.0.2".equals(members.get(0).getHostname()));
        Assert.assertTrue("tcp://192.168.0.2:0".equals(members.get(0).getName()));
    }
}
