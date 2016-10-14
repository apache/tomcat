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
package javax.el;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.res.StringManager;

public class TestImportHandler {

    /**
     * java.lang should be imported by default
     */
    @Test
    public void testResolveClass01() {
        ImportHandler handler = new ImportHandler();

        Class<?> result = handler.resolveClass("String");

        Assert.assertEquals(String.class, result);
    }


    /**
     * Resolve an unknown class
     */
    @Test
    public void testResolveClass02() {
        ImportHandler handler = new ImportHandler();

        Class<?> result = handler.resolveClass("Foo");

        Assert.assertNull(result);
    }


    /**
     * Conflict on resolution.
     */
    @Test
    public void testResolveClass03() {
        ImportHandler handler = new ImportHandler();

        handler.importPackage("org.apache.tomcat.util");
        handler.importPackage("org.apache.jasper.runtime");

        for (int i = 1; i <= 3; i++) {
            try {
                Class<?> clazz = handler.resolveClass("ExceptionUtils");
                Assert.fail("Expected ELException but got [" + clazz.getName()
                        + "] on iteration " + i);
            } catch (ELException ex) {
                // Expected
            }
        }
    }


    /**
     * Multiple package imports with a single match.
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=57113
     */
    @Test
    public void testResolveClass04() {
        ImportHandler handler = new ImportHandler();

        handler.importPackage("java.util");
        handler.importPackage("java.net");

        Class<?> clazz = handler.resolveClass("ArrayList");

        Assert.assertEquals(ArrayList.class, clazz);
    }


    /**
     * Attempting to resolve something that isn't a simple class name
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=57132
     */
    @Test
    public void testResolveClass05() {
        ImportHandler handler = new ImportHandler();

        handler.importPackage("java.nio");

        Class<?> clazz = handler.resolveClass("charset.StandardCharsets");

        Assert.assertNull(clazz);
    }

    /**
     * Attempting to resolve something that isn't a simple class name
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=57132
     */
    @Test
    public void testResolveClass06() {
        ImportHandler handler = new ImportHandler();

        handler.importPackage("java.nio");

        Class<?> clazz = handler.resolveClass(null);

        Assert.assertNull(clazz);
    }

    /**
     * Import a valid class.
     */
    @Test
    public void testImportClass01() {
        ImportHandler handler = new ImportHandler();

        handler.importClass("org.apache.tomcat.util.res.StringManager");

        Class<?> result = handler.resolveClass("StringManager");

        Assert.assertEquals(StringManager.class, result);
    }


    /**
     * Import an invalid class.
     */
    @Test
    public void testImportClass02() {
        ImportHandler handler = new ImportHandler();
        handler.importClass("org.apache.tomcat.util.res.StringManagerX");
        Class<?> result = handler.resolveClass("StringManagerX");
        Assert.assertNull(result);
    }


    /**
     * Import conflicting classes
     */
    @Test
    public void testImportClass03() {
        ImportHandler handler = new ImportHandler();

        handler.importClass("org.apache.tomcat.util.ExceptionUtils");
        for (int i = 1; i <= 3; i++) {
            try {
                handler.importClass("org.apache.jasper.util.ExceptionUtils");
                Assert.fail("Expected ELException but got none on iteration "
                        + i);
            } catch (ELException ex) {
                // Expected
            }
        }
    }


    /**
     * Import duplicate classes (i.e. the same class twice).
     */
    @Test
    public void testImportClass04() {
        ImportHandler handler = new ImportHandler();

        handler.importClass("org.apache.tomcat.util.res.StringManager");
        handler.importClass("org.apache.tomcat.util.res.StringManager");

        Class<?> result = handler.resolveClass("StringManager");

        Assert.assertEquals(StringManager.class, result);
    }


    /**
     * Import an invalid package.
     */
    @Test
    public void testImportPackage01_57574() {
        ImportHandler handler = new ImportHandler();

        handler.importPackage("org.apache.tomcat.foo");

        // No exception is expected
    }


    /**
     * Import a valid static field.
     */
    @Test
    public void testImportStatic01() {
        ImportHandler handler = new ImportHandler();

        handler.importStatic("org.apache.tomcat.util.scan.Constants.Package");

        Class<?> result = handler.resolveStatic("Package");

        Assert.assertEquals(org.apache.tomcat.util.scan.Constants.class, result);
    }


    /**
     * Import an invalid static field - does not exist.
     */
    @Test(expected=ELException.class)
    public void testImportStatic02() {
        ImportHandler handler = new ImportHandler();

        handler.importStatic("org.apache.tomcat.util.buf.Constants.PackageXX");
    }


    /**
     * Import an invalid static field - non-public.
     */
    @Test
    public void testImportStatic03() {
        ImportHandler handler = new ImportHandler();

        handler.importStatic("org.apache.tomcat.util.buf.Ascii.toLower");

        Class<?> result = handler.resolveStatic("toLower");

        Assert.assertEquals(org.apache.tomcat.util.buf.Ascii.class, result);
    }


    /**
     * Import an invalid static field - conflict.
     */
    @Test
    public void testImportStatic04() {
        ImportHandler handler = new ImportHandler();

        handler.importStatic("org.apache.tomcat.util.scan.Constants.Package");
        for (int i = 1; i <= 3; i++) {
            try {
                handler.importStatic("org.apache.tomcat.util.threads.Constants.Package");
                Assert.fail("Expected ELException but got none on iteration "
                        + i);
            } catch (ELException ex) {
                // Expected
            }
        }
    }


    /**
     * Package imports with conflicts due to non-public classes should not
     * conflict.
     */
    @Test
    public void testBug57135() {
        ImportHandler importHandler = new ImportHandler();

        importHandler.importPackage("util.a");
        importHandler.importPackage("util.b");

        importHandler.resolveClass("Foo");
    }
}
