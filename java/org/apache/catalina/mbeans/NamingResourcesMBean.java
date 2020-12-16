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
package org.apache.catalina.mbeans;

import java.util.ArrayList;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.deploy.NamingResourcesImpl</code> component.</p>
 *
 * @author Amy Roh
 */
public class NamingResourcesMBean extends BaseModelMBean {

    private static final StringManager sm = StringManager.getManager(NamingResourcesMBean.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * The configuration information registry for our managed beans.
     */
    protected final Registry registry = MBeanUtils.createRegistry();


    /**
     * The <code>ManagedBean</code> information describing this MBean.
     */
    protected final ManagedBean managed = registry.findManagedBean("NamingResources");


    // ------------------------------------------------------------- Attributes

    /**
     * Return the MBean Names of the set of defined environment entries for
     * this web application
     * @return an array of object names as strings
     */
    public String[] getEnvironments() {
        ContextEnvironment[] envs = ((NamingResourcesImpl)this.resource).findEnvironments();
        List<String> results = new ArrayList<>();
        for (ContextEnvironment env : envs) {
            try {
                ObjectName oname = MBeanUtils.createObjectName(managed.getDomain(), env);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException(
                        sm.getString("namingResourcesMBean.createObjectNameError.environment", env), e);
            }
        }
        return results.toArray(new String[0]);
    }


    /**
     * Return the MBean Names of all the defined resource references for this
     * application.
     * @return an array of object names as strings
     */
    public String[] getResources() {
        ContextResource[] resources = ((NamingResourcesImpl)this.resource).findResources();
        List<String> results = new ArrayList<>();
        for (ContextResource contextResource : resources) {
            try {
                ObjectName oname = MBeanUtils.createObjectName(managed.getDomain(), contextResource);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException(
                        sm.getString("namingResourcesMBean.createObjectNameError.resource", contextResource), e);
            }
        }
        return results.toArray(new String[0]);
    }


    /**
     * Return the MBean Names of all the defined resource link references for
     * this application.
     * @return an array of object names as strings
     */
    public String[] getResourceLinks() {
        ContextResourceLink[] resourceLinks =
                ((NamingResourcesImpl)this.resource).findResourceLinks();
        List<String> results = new ArrayList<>();
        for (ContextResourceLink resourceLink : resourceLinks) {
            try {
                ObjectName oname =
                        MBeanUtils.createObjectName(managed.getDomain(), resourceLink);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException(
                        sm.getString("namingResourcesMBean.createObjectNameError.resourceLink", resourceLink), e);
            }
        }
        return results.toArray(new String[0]);
    }


    // ------------------------------------------------------------- Operations

    /**
     * Add an environment entry for this web application.
     *
     * @param envName New environment entry name
     * @param type The type of the new environment entry
     * @param value The value of the new environment entry
     * @return the object name of the new environment entry
     * @throws MalformedObjectNameException if the object name was invalid
     */
    public String addEnvironment(String envName, String type, String value)
            throws MalformedObjectNameException {

        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return null;
        }
        ContextEnvironment env = nresources.findEnvironment(envName);
        if (env != null) {
            throw new IllegalArgumentException(sm.getString("namingResourcesMBean.addAlreadyExists.environment", envName));
        }
        env = new ContextEnvironment();
        env.setName(envName);
        env.setType(type);
        env.setValue(value);
        nresources.addEnvironment(env);

        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextEnvironment");
        ObjectName oname = MBeanUtils.createObjectName(managed.getDomain(), env);
        return oname.toString();
    }


    /**
     * Add a resource reference for this web application.
     *
     * @param resourceName New resource reference name
     * @param type New resource reference type
     * @return the object name of the new resource
     * @throws MalformedObjectNameException if the object name was invalid
     */
    public String addResource(String resourceName, String type)
            throws MalformedObjectNameException {

        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return null;
        }
        ContextResource resource = nresources.findResource(resourceName);
        if (resource != null) {
            throw new IllegalArgumentException(sm.getString("namingResourcesMBean.addAlreadyExists.resource", resourceName));
        }
        resource = new ContextResource();
        resource.setName(resourceName);
        resource.setType(type);
        nresources.addResource(resource);

        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextResource");
        ObjectName oname = MBeanUtils.createObjectName(managed.getDomain(), resource);
        return oname.toString();
    }


    /**
     * Add a resource link reference for this web application.
     *
     * @param resourceLinkName New resource link reference name
     * @param type New resource link reference type
     * @return the object name of the new resource link
     * @throws MalformedObjectNameException if the object name was invalid
     */
    public String addResourceLink(String resourceLinkName, String type)
        throws MalformedObjectNameException {

        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return null;
        }
        ContextResourceLink resourceLink =
                            nresources.findResourceLink(resourceLinkName);
        if (resourceLink != null) {
            throw new IllegalArgumentException(sm.getString("namingResourcesMBean.addAlreadyExists.resourceLink", resourceLinkName));
        }
        resourceLink = new ContextResourceLink();
        resourceLink.setName(resourceLinkName);
        resourceLink.setType(type);
        nresources.addResourceLink(resourceLink);

        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextResourceLink");
        ObjectName oname = MBeanUtils.createObjectName(managed.getDomain(), resourceLink);
        return oname.toString();
    }


    /**
     * Remove any environment entry with the specified name.
     *
     * @param envName Name of the environment entry to remove
     */
    public void removeEnvironment(String envName) {
        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return;
        }
        ContextEnvironment env = nresources.findEnvironment(envName);
        if (env == null) {
            throw new IllegalArgumentException(sm.getString("namingResourcesMBean.removeNotFound.environment", envName));
        }
        nresources.removeEnvironment(envName);
    }


    /**
     * Remove any resource reference with the specified name.
     *
     * @param resourceName Name of the resource reference to remove
     */
    public void removeResource(String resourceName) {
        resourceName = ObjectName.unquote(resourceName);
        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return;
        }
        ContextResource resource = nresources.findResource(resourceName);
        if (resource == null) {
            throw new IllegalArgumentException(sm.getString("namingResourcesMBean.removeNotFound.resource", resourceName));
        }
        nresources.removeResource(resourceName);
    }


    /**
     * Remove any resource link reference with the specified name.
     *
     * @param resourceLinkName Name of the resource link reference to remove
     */
    public void removeResourceLink(String resourceLinkName) {
        resourceLinkName = ObjectName.unquote(resourceLinkName);
        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return;
        }
        ContextResourceLink resourceLink = nresources.findResourceLink(resourceLinkName);
        if (resourceLink == null) {
            throw new IllegalArgumentException(sm.getString("namingResourcesMBean.removeNotFound.resourceLink", resourceLinkName));
        }
        nresources.removeResourceLink(resourceLinkName);
    }
}
