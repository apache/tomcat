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

import javax.naming.RefAddr;
import javax.naming.StringRefAddr;

/**
 * Represents a reference to lookup.
 */
public class LookupRef extends AbstractRef {

    private static final long serialVersionUID = 1L;

    /**
     * JNDI name for the lookup
     */
    public static final String LOOKUP_NAME = "lookup-name";


    public LookupRef(String resourceType, String lookupName) {
        this(resourceType, null, null, lookupName);
    }


    public LookupRef(String resourceType, String factory, String factoryLocation, String lookupName) {
        super(resourceType, factory, factoryLocation);
        if (lookupName != null && !lookupName.equals("")) {
            RefAddr ref = new StringRefAddr(LOOKUP_NAME, lookupName);
            add(ref);
        }
    }


    @Override
    protected String getDefaultFactoryClassName() {
        return org.apache.naming.factory.Constants.DEFAULT_LOOKUP_JNDI_FACTORY;
    }
}
