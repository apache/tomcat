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
package org.apache.tomcat;

import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;

/**
 * Interface for creating and managing instances of objects.
 */
public interface InstanceManager {

    /**
     * Create a new instance of the given class.
     *
     * @param clazz The class to instantiate
     * @return the new instance
     * @throws IllegalAccessException if the class or its nullary constructor is not accessible
     * @throws InvocationTargetException if the nullary constructor throws an exception
     * @throws NamingException if a naming exception is encountered
     * @throws InstantiationException if this Class represents an abstract class,
     *         an interface, an array class, a primitive type, or void
     * @throws IllegalArgumentException if this method is invoked with illegal arguments
     * @throws NoSuchMethodException if the nullary method cannot be found
     * @throws SecurityException if a security manager, s, is present
     */
    Object newInstance(Class<?> clazz) throws IllegalAccessException, InvocationTargetException, NamingException,
            InstantiationException, IllegalArgumentException, NoSuchMethodException, SecurityException;

    /**
     * Create a new instance of the class with the given name.
     *
     * @param className The name of the class to instantiate
     * @return the new instance
     * @throws IllegalAccessException if the class or its nullary constructor is not accessible
     * @throws InvocationTargetException if the nullary constructor throws an exception
     * @throws NamingException if a naming exception is encountered
     * @throws InstantiationException if this Class represents an abstract class,
     *         an interface, an array class, a primitive type, or void
     * @throws ClassNotFoundException if the class cannot be found
     * @throws IllegalArgumentException if this method is invoked with illegal arguments
     * @throws NoSuchMethodException if the nullary method cannot be found
     * @throws SecurityException if a security manager, s, is present
     */
    Object newInstance(String className)
            throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException,
            ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException;

    /**
     * Create a new instance of the class with the given name using the specified class loader.
     *
     * @param fqcn The fully qualified class name
     * @param classLoader The class loader to use for loading the class
     * @return the new instance
     * @throws IllegalAccessException if the class or its nullary constructor is not accessible
     * @throws InvocationTargetException if the nullary constructor throws an exception
     * @throws NamingException if a naming exception is encountered
     * @throws InstantiationException if this Class represents an abstract class,
     *         an interface, an array class, a primitive type, or void
     * @throws ClassNotFoundException if the class cannot be found
     * @throws IllegalArgumentException if this method is invoked with illegal arguments
     * @throws NoSuchMethodException if the nullary method cannot be found
     * @throws SecurityException if a security manager, s, is present
     */
    Object newInstance(String fqcn, ClassLoader classLoader)
            throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException,
            ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException;

    /**
     * Perform dependency injection on the given object.
     *
     * @param o The object to inject dependencies into
     * @throws IllegalAccessException if the class or its nullary constructor is not accessible
     * @throws InvocationTargetException if the nullary constructor throws an exception
     * @throws NamingException if a naming exception is encountered
     */
    void newInstance(Object o) throws IllegalAccessException, InvocationTargetException, NamingException;

    /**
     * Destroy the given instance, performing pre-destroy callbacks.
     *
     * @param o The object to destroy
     * @throws IllegalAccessException if the class or its nullary constructor is not accessible
     * @throws InvocationTargetException if the nullary constructor throws an exception
     */
    void destroyInstance(Object o) throws IllegalAccessException, InvocationTargetException;

    /**
     * Called by the component using the InstanceManager periodically to perform any regular maintenance that might be
     * required. By default, this method is a NO-OP.
     */
    default void backgroundProcess() {
        // NO-OP by default
    }
}
