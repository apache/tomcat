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



/**
 * Defines an interface for the object that is added to the representation of a
 * JNDI resource in web.xml to enable it to also be the implementation of that
 * JNDI resource. Only Catalina implements this interface but because the
 * web.xml representation is shared this interface has to be visible to Catalina
 * and Jasper.
 */
public interface NamingResources {

    /**
     * Add an environment entry for this web application.
     *
     * @param ce New environment entry
     */
    void addEnvironment(ContextEnvironment ce);

    /**
     * Remove any environment entry with the specified name.
     *
     * @param name Name of the environment entry to remove
     */
    void removeEnvironment(String name);

    /**
     * Add a resource reference for this web application.
     *
     * @param cr New resource reference
     */
    void addResource(ContextResource cr);

    /**
     * Remove any resource reference with the specified name.
     *
     * @param name Name of the resource reference to remove
     */
    void removeResource(String name);

    /**
     * Add a resource link for this web application.
     *
     * @param crl New resource link
     */
    void addResourceLink(ContextResourceLink crl);

    /**
     * Remove any resource link with the specified name.
     *
     * @param name Name of the resource link to remove
     */
    void removeResourceLink(String name);

    /**
     * @return the container with which the naming resources are associated.
     */
    Object getContainer();

}
