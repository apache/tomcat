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
package org.apache.catalina.ha.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardManager;
import org.apache.tomcat.unittest.TesterContext;

public class TestDeltaRequest {

    /*
     * Mostly interested in whether the attributes transfer correctly as the AttributeInfo class does not have a public
     * no-arg constructor. This shouldn't be necessary for a package private class (CheckStyle flags the use of a public
     * modifier as redundant) but SpotBugs complains as the class implements Externalizable. The purpose of this test is
     * to confirm that the SpotBugs warning is a false positive.
     */
    @Test
    public void testSerialization() throws Exception {
        // Create original request
        DeltaRequest original = new DeltaRequest();
        original.setSessionId("1234");
        original.setAttribute("A", "One");
        original.setAttribute("B", "Two");

        // Seralize original request
        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
            bytes = baos.toByteArray();
        }

        // Deserialize original request
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        DeltaRequest copyRequest = (DeltaRequest) ois.readObject();

        // Apply DeltaRequest so we can test that the attributes transferred correctly
        DeltaSession copySession = new DeltaSession();
        Manager manager = new StandardManager();
        manager.setContext(new TesterContext());
        copySession.setManager(manager);
        copySession.setId("1234", false);
        copySession.setValid(true);
        copyRequest.execute(copySession, false);

        // Test for the attributes
        Assert.assertEquals("One", copySession.getAttribute("A"));
        Assert.assertEquals("Two", copySession.getAttribute("B"));
    }
}
