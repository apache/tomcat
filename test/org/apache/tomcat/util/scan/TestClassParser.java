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
package org.apache.tomcat.util.scan;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestClassParser extends TomcatBaseTest {

    @Target({ElementType.TYPE, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Foo {
    }

    /** and beans */
    @Foo
    public class OnClass {
    }

    public class OnField {
        @Foo
        private String name;
    }

    @Test
    public void testAnnotations() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File("test/webapp-sci");
        tomcat.addWebapp("", appDir.getAbsolutePath());
        tomcat.start();
        Assert.assertTrue(FooSCI.classSet.size() == 2);
        Assert.assertTrue(FooSCI.classSet.contains(OnClass.class));
        Assert.assertTrue(FooSCI.classSet.contains(OnField.class));
    }
}
