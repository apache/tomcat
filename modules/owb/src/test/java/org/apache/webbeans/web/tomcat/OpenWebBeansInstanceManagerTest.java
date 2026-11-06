/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.webbeans.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;

import org.apache.tomcat.InstanceManager;
import org.junit.jupiter.api.Test;

class OpenWebBeansInstanceManagerTest {

    @Test
    void newInstanceByClassDelegates() throws Exception {
        RecordingInstanceManager delegate = new RecordingInstanceManager();
        OpenWebBeansInstanceManager manager = new OpenWebBeansInstanceManager(
                getClass().getClassLoader(), delegate);

        Object instance = manager.newInstance(TestBean.class);

        assertEquals(1, delegate.classInstances);
        assertInstanceOf(TestBean.class, instance);
    }

    @Test
    void newInstanceByClassNameDelegates() throws Exception {
        RecordingInstanceManager delegate = new RecordingInstanceManager();
        OpenWebBeansInstanceManager manager = new OpenWebBeansInstanceManager(
                getClass().getClassLoader(), delegate);

        Object instance = manager.newInstance(TestBean.class.getName());

        assertEquals(1, delegate.stringInstances);
        assertInstanceOf(TestBean.class, instance);
    }

    @Test
    void newInstanceByClassNameAndClassLoaderDelegates() throws Exception {
        RecordingInstanceManager delegate = new RecordingInstanceManager();
        OpenWebBeansInstanceManager manager = new OpenWebBeansInstanceManager(
                getClass().getClassLoader(), delegate);

        Object instance = manager.newInstance(TestBean.class.getName(), getClass().getClassLoader());

        assertEquals(1, delegate.stringLoaderInstances);
        assertInstanceOf(TestBean.class, instance);
    }

    @Test
    void newInstanceByObjectDoesNotDelegateCreation() throws Exception {
        RecordingInstanceManager delegate = new RecordingInstanceManager();
        OpenWebBeansInstanceManager manager = new OpenWebBeansInstanceManager(
                getClass().getClassLoader(), delegate);
        TestBean instance = new TestBean();

        manager.newInstance(instance);

        assertEquals(0, delegate.objectInstances);
        assertTrue(delegate.destroyed.isEmpty());
    }

    @Test
    void destroyInstanceAlwaysDelegates() throws Exception {
        RecordingInstanceManager delegate = new RecordingInstanceManager();
        OpenWebBeansInstanceManager manager = new OpenWebBeansInstanceManager(
                getClass().getClassLoader(), delegate);
        TestBean instance = new TestBean();

        manager.destroyInstance(instance);

        assertEquals(List.of(instance), delegate.destroyed);
    }

    @Test
    void destroyKnownInstanceDelegates() throws Exception {
        RecordingInstanceManager delegate = new RecordingInstanceManager();
        OpenWebBeansInstanceManager manager = new OpenWebBeansInstanceManager(
                getClass().getClassLoader(), delegate);

        Object instance = manager.newInstance(TestBean.class);
        manager.destroyInstance(instance);

        assertEquals(List.of(instance), delegate.destroyed);
    }

    static class TestBean {
    }

    static class RecordingInstanceManager implements InstanceManager {

        private int classInstances;
        private int stringInstances;
        private int stringLoaderInstances;
        private int objectInstances;
        private final List<Object> destroyed = new ArrayList<>();

        @Override
        public Object newInstance(Class<?> clazz) throws IllegalAccessException, InvocationTargetException,
                NamingException, InstantiationException {
            classInstances++;
            return new TestBean();
        }

        @Override
        public Object newInstance(String className) throws IllegalAccessException, InvocationTargetException,
                NamingException, InstantiationException, ClassNotFoundException {
            stringInstances++;
            return new TestBean();
        }

        @Override
        public void newInstance(Object object) throws IllegalAccessException, InvocationTargetException,
                NamingException {
            objectInstances++;
        }

        @Override
        public Object newInstance(String className, ClassLoader classLoader)
                throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException,
                ClassNotFoundException {
            stringLoaderInstances++;
            return new TestBean();
        }

        @Override
        public void destroyInstance(Object object) throws IllegalAccessException, InvocationTargetException {
            destroyed.add(object);
        }
    }
}
