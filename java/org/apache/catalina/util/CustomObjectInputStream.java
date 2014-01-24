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

package org.apache.catalina.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;

/**
 * Custom subclass of <code>ObjectInputStream</code> that loads from the
 * class loader for this web application.  This allows classes defined only
 * with the web application to be found correctly.
 *
 * @author Craig R. McClanahan
 * @author Bip Thelin
 */
public final class CustomObjectInputStream
    extends ObjectInputStream {


    /**
     * The class loader we will use to resolve classes.
     */
    private final ClassLoader classLoader;


    /**
     * Construct a new instance of CustomObjectInputStream
     *
     * @param stream The input stream we will read from
     * @param classLoader The class loader used to instantiate objects
     *
     * @exception IOException if an input/output error occurs
     */
    public CustomObjectInputStream(InputStream stream,
                                   ClassLoader classLoader)
        throws IOException {

        super(stream);
        this.classLoader = classLoader;
    }


    /**
     * Load the local class equivalent of the specified stream class
     * description, by using the class loader assigned to this Context.
     *
     * @param classDesc Class description from the input stream
     *
     * @exception ClassNotFoundException if this class cannot be found
     * @exception IOException if an input/output error occurs
     */
    @Override
    public Class<?> resolveClass(ObjectStreamClass classDesc)
        throws ClassNotFoundException, IOException {
        try {
            return Class.forName(classDesc.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            try {
                // Try also the superclass because of primitive types
                return super.resolveClass(classDesc);
            } catch (ClassNotFoundException e2) {
                // Rethrow original exception, as it can have more information
                // about why the class was not found. BZ 48007
                throw e;
            }
        }
    }


    /**
     * Return a proxy class that implements the interfaces named in a proxy
     * class descriptor. Do this using the class loader assigned to this
     * Context.
     */
    @Override
    protected Class<?> resolveProxyClass(String[] interfaces)
        throws IOException, ClassNotFoundException {

        Class<?>[] cinterfaces = new Class[interfaces.length];
        for (int i = 0; i < interfaces.length; i++)
            cinterfaces[i] = classLoader.loadClass(interfaces[i]);

        try {
            return Proxy.getProxyClass(classLoader, cinterfaces);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(null, e);
        }
    }

}
