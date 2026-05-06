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
package org.apache.naming;

import java.util.Enumeration;

import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;

/**
 * Abstract base class for {@link Reference} implementations used by the naming
 * context. Provides a default factory class name lookup mechanism.
 */
public abstract class AbstractRef extends Reference {

    private static final long serialVersionUID = 1L;


    /**
     * Constructs a new <code>AbstractRef</code> with the specified class name.
     *
     * @param className The non-null class name
     */
    public AbstractRef(String className) {
        super(className);
    }


    /**
     * Constructs a new <code>AbstractRef</code> with the specified class name,
     * factory class name, and factory location.
     *
     * @param className The non-null class name
     * @param factory The factory class name
     * @param factoryLocation The factory location
     */
    public AbstractRef(String className, String factory, String factoryLocation) {
        super(className, factory, factoryLocation);
    }


    @Override
    public final String getFactoryClassName() {
        String factory = super.getFactoryClassName();
        if (factory != null) {
            return factory;
        } else {
            factory = System.getProperty(Context.OBJECT_FACTORIES);
            if (factory != null) {
                return null;
            } else {
                return getDefaultFactoryClassName();
            }
        }
    }


    /**
     * Returns the default factory class name.
     *
     * @return the default factory class name
     */
    protected abstract String getDefaultFactoryClassName();


    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());
        sb.append("[className=");
        sb.append(getClassName());
        sb.append(",factoryClassLocation=");
        sb.append(getFactoryClassLocation());
        sb.append(",factoryClassName=");
        sb.append(getFactoryClassName());
        Enumeration<RefAddr> refAddrs = getAll();
        while (refAddrs.hasMoreElements()) {
            RefAddr refAddr = refAddrs.nextElement();
            sb.append(",{type=");
            sb.append(refAddr.getType());
            sb.append(",content=");
            sb.append(refAddr.getContent());
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }
}
