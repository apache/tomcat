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
package org.apache.el;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * Defines a copy of a single class in a class loader of its own so that tests can check that the class, and therefore
 * the class loader, is not kept in memory by a cache.
 */
public class TesterIsolatedClassLoader extends ClassLoader {

    private final String isolatedClassName;


    public TesterIsolatedClassLoader(Class<?> isolatedClass) {
        super(isolatedClass.getClassLoader());
        this.isolatedClassName = isolatedClass.getName();
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!isolatedClassName.equals(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                try (InputStream is = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = is.readAllBytes();
                    result = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            return result;
        }
    }


    /**
     * Waits until the referenced object has been garbage collected.
     *
     * @param reference The reference to the object
     *
     * @return {@code true} if the object was garbage collected, otherwise {@code false}
     *
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    public static boolean awaitCollection(WeakReference<?> reference) throws InterruptedException {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(100);
        }
        return reference.get() == null;
    }


    /**
     * The class that is defined in an isolated class loader.
     */
    public static class Target {

        public String echo(String value) {
            return value;
        }
    }
}
