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

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.LookupRef;
import org.apache.naming.StringManager;

/**
 * Object factory for lookups.
 */
public class LookupFactory implements ObjectFactory {

    private static final Log log = LogFactory.getLog(LookupFactory.class);
    private static final StringManager sm = StringManager.getManager(LookupFactory.class);

    private static final ThreadLocal<Set<String>> names = new ThreadLocal<Set<String>>() {

        @Override
        protected Set<String> initialValue() {
            return new HashSet<>();
        }
    };

    /**
     * Create a new Resource env instance.
     *
     * @param obj The reference object describing the DataSource
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
            Hashtable<?, ?> environment) throws Exception {

        String lookupName = null;
        Object result = null;

        if (obj instanceof LookupRef) {
            Reference ref = (Reference) obj;
            ObjectFactory factory = null;
            RefAddr lookupNameRefAddr = ref.get(LookupRef.LOOKUP_NAME);
            if (lookupNameRefAddr != null) {
                lookupName = lookupNameRefAddr.getContent().toString();
            }

            try {
                if (lookupName != null) {
                    if (!names.get().add(lookupName)) {
                        String msg = sm.getString("lookupFactory.circularReference", lookupName);
                        NamingException ne = new NamingException(msg);
                        log.warn(msg, ne);
                        throw ne;
                    }
                }
                RefAddr factoryRefAddr = ref.get(Constants.FACTORY);
                if (factoryRefAddr != null) {
                    // Using the specified factory
                    String factoryClassName = factoryRefAddr.getContent().toString();
                    // Loading factory
                    ClassLoader tcl = Thread.currentThread().getContextClassLoader();
                    Class<?> factoryClass = null;
                    if (tcl != null) {
                        try {
                            factoryClass = tcl.loadClass(factoryClassName);
                        } catch (ClassNotFoundException e) {
                            NamingException ex = new NamingException(
                                    sm.getString("lookupFactory.loadFailed"));
                            ex.initCause(e);
                            throw ex;
                        }
                    } else {
                        try {
                            factoryClass = Class.forName(factoryClassName);
                        } catch (ClassNotFoundException e) {
                            NamingException ex = new NamingException(
                                    sm.getString("lookupFactory.loadFailed"));
                            ex.initCause(e);
                            throw ex;
                        }
                    }
                    if (factoryClass != null) {
                        try {
                            factory = (ObjectFactory) factoryClass.newInstance();
                        } catch (Throwable t) {
                            if (t instanceof NamingException)
                                throw (NamingException) t;
                            NamingException ex = new NamingException(
                                    sm.getString("lookupFactory.createFailed"));
                            ex.initCause(t);
                            throw ex;
                        }
                    }
                }
                // Note: No defaults here
                if (factory != null) {
                    result = factory.getObjectInstance(obj, name, nameCtx, environment);
                } else {
                    if (lookupName == null) {
                        throw new NamingException(sm.getString("lookupFactory.createFailed"));
                    } else {
                        result = new InitialContext().lookup(lookupName);
                    }
                }

                Class<?> clazz = Class.forName(ref.getClassName());
                if (result != null && !clazz.isAssignableFrom(result.getClass())) {
                    String msg = sm.getString("lookupFactory.typeMismatch",
                            name, ref.getClassName(), lookupName, result.getClass().getName());
                    NamingException ne = new NamingException(msg);
                    log.warn(msg, ne);
                    throw ne;
                }
            } finally {
                names.get().remove(lookupName);
            }
        }


        return result;
    }
}
