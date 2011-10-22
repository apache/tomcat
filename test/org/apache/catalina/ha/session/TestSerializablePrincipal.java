/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.catalina.ha.session;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import org.junit.Test;

import org.apache.catalina.realm.GenericPrincipal;


public class TestSerializablePrincipal  {

    /**
     * Simple serialization / de-serialization test for bug 43840.
     */
    @SuppressWarnings("null")
    @Test
    public void testWriteReadPrincipal() {
        // Get a temporary file to use for the serialization test
        File file = null;
        try {
            file = File.createTempFile("ser", null);
            file.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
            fail("ioe creating temporary file");
        }

        // Create the Principal to serialize
        List<String> roles = new ArrayList<String>();
        roles.add("RoleA");
        roles.add("RoleB");
        TesterPrincipal tpOriginal = new TesterPrincipal("inner");
        GenericPrincipal gpOriginal =
            new GenericPrincipal("usr", "pwd", roles, tpOriginal);

        // Do the serialization
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            SerializablePrincipal.writePrincipal(gpOriginal, oos);
            oos.close();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail("fnfe creating object output stream");
        } catch (IOException e) {
            e.printStackTrace();
            fail("ioe serializing principal");
        }

        // De-serialize the Principal
        GenericPrincipal gpNew = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            gpNew = SerializablePrincipal.readPrincipal(ois);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail("fnfe reading object output stream");
        } catch (IOException e) {
            e.printStackTrace();
            fail("ioe de-serializing principal");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            fail("cnfe de-serializing principal");
        }

        // Now test how similar original and de-serialized versions are
        assertEquals("User names different", gpOriginal.getName(),
                gpNew.getName());
        assertEquals("Passwords different", gpOriginal.getPassword(),
                gpNew.getPassword());
        assertEquals("Number of roles different", gpOriginal.getRoles().length,
                gpNew.getRoles().length);
        for (int i = 0; i < gpOriginal.getRoles().length; i++) {
            assertEquals("Role name index " + i + "different",
                    gpOriginal.getRoles()[i], gpNew.getRoles()[i]);
        }
        // These are the key tests for bug 43840
        assertNotSame("Inner principal not present", gpNew,
                gpNew.getUserPrincipal());
        assertEquals("Inner user names are different", tpOriginal.getName(),
                gpNew.getUserPrincipal().getName());
    }

}
