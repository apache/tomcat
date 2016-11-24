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

import javax.naming.StringRefAddr;

/**
 * Represents a reference address to a resource.
 *
 * @author Remy Maucherat
 */
public class ResourceLinkRef extends AbstractRef {

    private static final long serialVersionUID = 1L;


    /**
     * Default factory for this reference.
     */
    public static final String DEFAULT_FACTORY =
            org.apache.naming.factory.Constants.DEFAULT_RESOURCE_LINK_FACTORY;


    /**
     * Description address type.
     */
    public static final String GLOBALNAME = "globalName";


    /**
     * ResourceLink Reference.
     *
     * @param resourceClass Resource class
     * @param globalName Global name
     * @param factory The possibly null class name of the object's factory.
     * @param factoryLocation The possibly null location from which to load the
     *                        factory (e.g. URL)
     */
    public ResourceLinkRef(String resourceClass, String globalName,
            String factory, String factoryLocation) {
        super(resourceClass, factory, factoryLocation);
        StringRefAddr refAddr = null;
        if (globalName != null) {
            refAddr = new StringRefAddr(GLOBALNAME, globalName);
            add(refAddr);
        }
    }


    @Override
    protected String getDefaultFactoryClassName() {
        return DEFAULT_FACTORY;
    }
}
