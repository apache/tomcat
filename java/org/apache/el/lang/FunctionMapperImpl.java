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
package org.apache.el.lang;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.el.FunctionMapper;

import org.apache.el.util.MessageFactory;
import org.apache.el.util.ReflectionUtil;

/**
 * Thread-safe implementation of FunctionMapper that supports externalization.
 */
public class FunctionMapperImpl extends FunctionMapper implements Externalizable {

    private static final long serialVersionUID = 1L;

    /**
     * Map of function keys to their Function instances.
     */
    protected ConcurrentMap<String,Function> functions = new ConcurrentHashMap<>();

    /**
     * Creates a new empty function mapper.
     */
    public FunctionMapperImpl() {
        // Default constructor required by Externalizable
    }

    @Override
    public Method resolveFunction(String prefix, String localName) {
        Function f = this.functions.get(prefix + ":" + localName);
        if (f == null) {
            return null;
        }
        return f.getMethod();
    }

    @Override
    public void mapFunction(String prefix, String localName, Method m) {
        String key = prefix + ":" + localName;
        if (m == null) {
            functions.remove(key);
        } else {
            Function f = new Function(prefix, localName, m);
            functions.put(key, f);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(this.functions);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.functions = (ConcurrentMap<String,Function>) in.readObject();
    }

    /**
     * Represents a mapped EL function with serialization support.
     */
    public static class Function implements Externalizable {

        /**
         * The resolved method, transient as it cannot be serialized directly.
         */
        protected transient Method m;
        /**
         * The declaring class name of the method.
         */
        protected String owner;
        /**
         * The method name.
         */
        protected String name;
        /**
         * The parameter type names of the method.
         */
        protected String[] types;
        /**
         * The function namespace prefix.
         */
        protected String prefix;
        /**
         * The local function name.
         */
        protected String localName;

        /**
         * Creates a new function mapping for the given method.
         *
         * @param prefix The namespace prefix
         * @param localName The local function name
         * @param m The method to map
         */
        public Function(String prefix, String localName, Method m) {
            if (localName == null) {
                throw new NullPointerException(MessageFactory.get("error.nullLocalName"));
            }
            if (m == null) {
                throw new NullPointerException(MessageFactory.get("error.nullMethod"));
            }
            this.prefix = prefix;
            this.localName = localName;
            this.m = m;
        }

        /**
         * Default constructor required by Externalizable for deserialization.
         */
        public Function() {
            // for serialization
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF((this.prefix != null) ? this.prefix : "");
            out.writeUTF(this.localName);
            if (this.owner != null && this.name != null && this.types != null) {
                out.writeUTF(this.owner);
                out.writeUTF(this.name);
                out.writeObject(this.types);
            } else {
                out.writeUTF(this.m.getDeclaringClass().getName());
                out.writeUTF(this.m.getName());
                out.writeObject(ReflectionUtil.toTypeNameArray(this.m.getParameterTypes()));
            }
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

            this.prefix = in.readUTF();
            if (this.prefix.isEmpty()) {
                this.prefix = null;
            }
            this.localName = in.readUTF();
            this.owner = in.readUTF();
            this.name = in.readUTF();
            this.types = (String[]) in.readObject();
        }

        /**
         * Gets the resolved method, lazily loading it from serialized data if needed.
         *
         * @return The resolved method, or {@code null} if it could not be resolved
         */
        public Method getMethod() {
            if (this.m == null) {
                try {
                    Class<?> t = ReflectionUtil.forName(this.owner);
                    Class<?>[] p = ReflectionUtil.toTypeArray(this.types);
                    this.m = t.getMethod(this.name, p);
                } catch (Exception e) {
                    // Ignore: this results in ELException after further resolution
                }
            }
            return this.m;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Function) {
                return this.hashCode() == obj.hashCode();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.prefix + this.localName).hashCode();
        }
    }

}
