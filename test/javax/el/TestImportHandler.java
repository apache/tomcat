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
    @Test(expected=ELException.class)
    public void testResolveClass03() {
        ImportHandler handler = new ImportHandler();

        handler.importPackage("org.apache.tomcat.util");
        handler.importPackage("org.apache.jasper.util");

        handler.resolveClass("ExceptionUtils");
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
    @Test(expected=ELException.class)
    public void testImportClass02() {
        ImportHandler handler = new ImportHandler();

        handler.importClass("org.apache.tomcat.util.res.StringManagerX");
    }


    /**
     * Import conflicting classes
     */
    @Test(expected=ELException.class)
    public void testImportClass03() {
        ImportHandler handler = new ImportHandler();

        handler.importClass("org.apache.tomcat.util.ExceptionUtils");
        handler.importClass("org.apache.jasper.util.ExceptionUtils");
    }


    /**
     * Import an invalid package.
     */
    @Test(expected=ELException.class)
    public void testImportPackage01() {
        ImportHandler handler = new ImportHandler();

        handler.importPackage("org.apache.tomcat.foo");
    }


    /**
     * Import a valid static field.
     */
    @Test
    public void testImportStatic01() {
        ImportHandler handler = new ImportHandler();

        handler.importStatic("org.apache.tomcat.util.buf.Constants.Package");

        Class<?> result = handler.resolveStatic("Package");

        Assert.assertEquals(org.apache.tomcat.util.buf.Constants.class, result);
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
    @Test(expected=ELException.class)
    public void testImportStatic04() {
        ImportHandler handler = new ImportHandler();

        handler.importStatic("org.apache.tomcat.util.buf.Constants.Package");
        handler.importStatic("org.apache.tomcat.util.scan.Constants.Package");
    }
}
