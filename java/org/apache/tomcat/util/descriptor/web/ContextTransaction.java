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
package org.apache.tomcat.util.descriptor.web;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Representation of an application resource reference, as represented in
 * an <code>&lt;res-env-ref&gt;</code> element in the deployment descriptor.
 *
 * @author Craig R. McClanahan
 */
public class ContextTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * Holder for our configured properties.
     */
    private final Map<String, Object> properties = new HashMap<>();

    /**
     * @param name The property name
     * @return a configured property.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Set a configured property.
     * @param name The property name
     * @param value The property value
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Remove a configured property.
     * @param name The property name
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    /**
     * List properties.
     * @return the property names iterator
     */
    public Iterator<String> listProperties() {
        return properties.keySet().iterator();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {
        return "Transaction[]";
    }
}
