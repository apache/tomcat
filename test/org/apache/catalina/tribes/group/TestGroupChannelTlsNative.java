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
package org.apache.catalina.tribes.group;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.tomcat.jni.AprStatus;
import org.apache.tomcat.jni.Library;

@RunWith(Parameterized.class)
public class TestGroupChannelTlsNative extends GroupChannelTlsTestBase {

    private static Lifecycle DUMMY_LIFECYCLE = new StandardServer();

    private LifecycleListener listener;

    @Before
    public void setup() {
        // Need to make sure FFM is not used.
        System.setProperty("org.apache.tomcat.util.openssl.LIBRARY_NAME", "does-not-exist");

        if (explicit) {
            // Start Native Library
            listener = new AprLifecycleListener();
            listener.lifecycleEvent(new LifecycleEvent(DUMMY_LIFECYCLE, Lifecycle.BEFORE_INIT_EVENT, null));
            Assume.assumeTrue(AprStatus.isAprAvailable());
            if (Library.TCN_MAJOR_VERSION < 2 || Library.TCN_MAJOR_VERSION == 2 && Library.TCN_PATCH_VERSION < 17) {
                listener.lifecycleEvent(new LifecycleEvent(DUMMY_LIFECYCLE, Lifecycle.AFTER_DESTROY_EVENT, null));
                Assume.assumeTrue("Tomvcat Native >= 2.0.17 required", false);
            }
        } else {
            // Need to make sure FFM is not used.
            System.setProperty("org.apache.tomcat.util.openssl.LIBRARY_NAME", "does-not-exist");
            // Test Native Library
            LifecycleListener listener = new AprLifecycleListener();
            listener.lifecycleEvent(new LifecycleEvent(DUMMY_LIFECYCLE, Lifecycle.BEFORE_INIT_EVENT, null));
            Assume.assumeTrue(AprStatus.isAprAvailable());
            listener.lifecycleEvent(new LifecycleEvent(DUMMY_LIFECYCLE, Lifecycle.AFTER_DESTROY_EVENT, null));
            Assume.assumeFalse("Tomvcat Native >= 2.0.17 required",
                    Library.TCN_MAJOR_VERSION < 2 || Library.TCN_MAJOR_VERSION == 2 && Library.TCN_PATCH_VERSION < 17);
        }
    }


    @After
    public void teardown() {
        if (explicit) {
            // Models AprLifecycleListener. Stop Native Library.
            listener.lifecycleEvent(new LifecycleEvent(DUMMY_LIFECYCLE, Lifecycle.AFTER_DESTROY_EVENT, null));
        }
    }
}
