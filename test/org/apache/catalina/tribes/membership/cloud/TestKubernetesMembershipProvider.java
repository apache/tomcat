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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.tribes.group.GroupChannel;

public class TestKubernetesMembershipProvider {

    /*
     * When the service account token cannot be read, the provider has no way to access the
     * Kubernetes API and never recovers, as the stream provider is only created at start. Such a
     * node stays out of the membership for its whole lifetime while the other nodes see its pod
     * and keep replicating to it, turning replication one way. The start must therefore fail
     * closed, like it does when the client certificate setup fails.
     */
    @Test
    public void testStartFailsWhenServiceAccountTokenUnreadable() throws Exception {
        // The provider reads the default token file when the environment does not redirect it, so
        // the test needs an environment without the file and without a redirect to another one
        Assume.assumeTrue(System.getenv(CloudMembershipProvider.CUSTOM_ENV_PREFIX + "SA_TOKEN_FILE") == null);
        Assume.assumeTrue(System.getenv("SA_TOKEN_FILE") == null);
        Assume.assumeTrue(
                System.getenv(CloudMembershipProvider.CUSTOM_ENV_PREFIX + "CLIENT_CERT_FILE") == null);
        Assume.assumeTrue(System.getenv("KUBERNETES_CLIENT_CERTIFICATE_FILE") == null);
        Assume.assumeFalse(Files.exists(
                FileSystems.getDefault().getPath("/var/run/secrets/kubernetes.io/serviceaccount/token")));

        GroupChannel channel = new GroupChannel();
        channel.setName("TestKubernetesMembershipProvider-" + System.nanoTime());

        CloudMembershipService service = new CloudMembershipService();
        service.setChannel(channel);
        service.setLocalMemberProperties("127.0.0.1", 4006, 0, 0);
        try {
            service.start(CloudMembershipService.MBR_RX);
            Assert.fail("start() must fail when the service account token cannot be read");
        } catch (IllegalStateException expected) {
            // The start fails closed rather than running without access to the API
            Assert.assertTrue(expected.getCause() instanceof IOException);
        }
    }
}
