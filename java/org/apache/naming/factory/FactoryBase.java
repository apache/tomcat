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

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * Abstract base class that provides common functionality required by
 * sub-classes. This class exists primarily to reduce code duplication.
 */
public abstract class FactoryBase implements ObjectFactory {

    /**
     * Creates a new object instance.
     *
     * @param obj The reference object describing the object to create
     */
    @Override
    public final Object getObjectInstance(Object obj, Name name, Context nameCtx,
            Hashtable<?,?> environment) throws Exception {

        if (isReferenceTypeSupported(obj)) {
            Reference ref = (Reference) obj;

            Object linked = getLinked(ref);
            if (linked != null) {
                return linked;
            }

            ObjectFactory factory = null;
            RefAddr factoryRefAddr = ref.get(Constants.FACTORY);
            if (factoryRefAddr != null) {
                // Using the specified factory
                String factoryClassName = factoryRefAddr.getContent().toString();
                // Loading factory
                ClassLoader tcl = Thread.currentThread().getContextClassLoader();
                Class<?> factoryClass = null;
                try {
                    if (tcl != null) {
                        factoryClass = tcl.loadClass(factoryClassName);
                    } else {
                        factoryClass = Class.forName(factoryClassName);
                    }
                } catch(ClassNotFoundException e) {
                    NamingException ex = new NamingException(
                            "Could not load resource factory class");
                    ex.initCause(e);
                    throw ex;
                }
                try {
                    factory = (ObjectFactory) factoryClass.newInstance();
                } catch(Throwable t) {
                    if (t instanceof NamingException) {
                        throw (NamingException) t;
                    }
                    if (t instanceof ThreadDeath) {
                        throw (ThreadDeath) t;
                    }
                    if (t instanceof VirtualMachineError) {
                        throw (VirtualMachineError) t;
                    }
                    NamingException ex = new NamingException(
                            "Could not create resource factory instance");
                    ex.initCause(t);
                    throw ex;
                }
            } else {
                // Check for a default factory
                factory = getDefaultFactory(ref);
            }

            if (factory != null) {
                return factory.getObjectInstance(obj, name, nameCtx, environment);
            } else {
                throw new NamingException("Cannot create resource instance");
            }
        }

        return null;
    }


    /**
     * Determines if this factory supports processing the provided reference
     * object.
     *
     * @param obj   The object to be processed
     *
     * @return <code>true</code> if this factory can process the object,
     *         otherwise <code>false</code>
     */
    protected abstract boolean isReferenceTypeSupported(Object obj);

    /**
     * If a default factory is available for the given reference type, create
     * the default factory.
     *
     * @param ref   The reference object to be processed
     *
     * @return  The default factory for the given reference object or
     *          <code>null</code> if no default factory exists.
     *
     * @throws NamingException  If the default factory can not be craeted
     */
    protected abstract ObjectFactory getDefaultFactory(Reference ref)
            throws NamingException;

    /**
     * If this reference is a link to another JNDI object, obtain that object.
     *
     * @param ref   The reference object to be processed
     *
     * @return  The linked object or <code>null</code> if linked objects are
     *          not supported by or not configured for this reference object
     */
    protected abstract Object getLinked(Reference ref) throws NamingException;
}
