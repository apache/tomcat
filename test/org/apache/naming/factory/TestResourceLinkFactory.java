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
package org.apache.naming.factory;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.apache.naming.ResourceLinkRef;

public class TestResourceLinkFactory {

    /*
     * Regression test for the case where a resource link has been registered
     * before the global context has been set, e.g. when an embedded Tomcat
     * initialises the container before the naming context for the server is
     * created. Prior to the fix, this resulted in a null pointer exception.
     */
    @Test
    public void testGetResourceLinkWithoutGlobalContext() throws Exception {
        // Arrange: no global context is available and the link is registered
        ResourceLinkFactory.setGlobalContext(null);
        ResourceLinkFactory.registerGlobalResourceAccess(null, "link", "global");
        ResourceLinkRef ref = new ResourceLinkRef("type", "global", null, null);
        ResourceLinkFactory factory = new ResourceLinkFactory();
        // Act
        Object result = factory.getObjectInstance(ref, null, null, null);
        // Assert
        // Since the global context is not available, the link can not be
        // resolved
        Assert.assertNull(result);
    }

    @After
    public void tearDown() {
        // Remove the registration made by the test so that other tests in the
        // same JVM are not affected by it
        ResourceLinkFactory.deregisterGlobalResourceAccess(null, "link");
    }
}
