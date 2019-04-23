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
package org.apache.tomcat.util.modeler;

import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;

/**
 * An implementation of the MBean registry that effectively disables MBean
 * registration. This is typically used when low memory footprint is a primary
 * concern.
 */
public class NoDescriptorRegistry extends Registry {

    private final MBeanServer mBeanServer = new NoJmxMBeanServer();
    private final ManagedBean defaultMBean = new PassthroughMBean();


    @Override
    public void registerComponent(final Object bean, final String oname, final String type)
            throws Exception {
        // no-op
    }


    @Override
    public void unregisterComponent(final String oname) {
        // no-op
    }


    @Override
    public void invoke(final List<ObjectName> mbeans, final String operation,
            final boolean failFirst) throws Exception {
        // no-op
    }


    @SuppressWarnings("sync-override")
    @Override
    public int getId(final String domain, final String name) {
        return 0;
    }


    @Override
    public void addManagedBean(final ManagedBean bean) {
        // no-op
    }


    @Override
    public ManagedBean findManagedBean(final String name) {
        return defaultMBean;
    }


    @Override
    public String getType(final ObjectName oname, final String attName) {
        return null;
    }


    @Override
    public MBeanOperationInfo getMethodInfo(final ObjectName oname, final String opName) {
        return null;
    }


    @Override
    public ManagedBean findManagedBean(final Object bean, final Class<?> beanClass,
            final String type) throws Exception {
        return null;
    }


    @Override
    public List<ObjectName> load(final String sourceType, final Object source, final String param)
            throws Exception {
        return Collections.emptyList();
    }


    @Override
    public void loadDescriptors(final String packageName, final ClassLoader classLoader) {
        // no-op
    }


    @Override
    public void registerComponent(final Object bean, final ObjectName oname, final String type)
            throws Exception {
        // no-op
    }


    @Override
    public void unregisterComponent(final ObjectName oname) {
        // no-op
    }


    @Override
    public MBeanServer getMBeanServer() {
        return mBeanServer;
    }

    private static class NoJmxMBeanServer implements MBeanServer {

        @Override
        public ObjectInstance createMBean(String className, ObjectName name)
                throws ReflectionException, InstanceAlreadyExistsException,
                MBeanRegistrationException, NotCompliantMBeanException, MBeanRegistrationException {
            return null;
        }


        @Override
        public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName)
                throws ReflectionException, InstanceAlreadyExistsException,
                MBeanRegistrationException, NotCompliantMBeanException, InstanceNotFoundException,
                MBeanRegistrationException {
            return null;
        }


        @Override
        public ObjectInstance createMBean(String className, ObjectName name, Object[] params,
                String[] signature) throws ReflectionException, InstanceAlreadyExistsException,
                MBeanRegistrationException, NotCompliantMBeanException, MBeanRegistrationException {
            return null;
        }


        @Override
        public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName,
                Object[] params, String[] signature) throws ReflectionException,
                InstanceAlreadyExistsException, MBeanRegistrationException,
                NotCompliantMBeanException, InstanceNotFoundException, MBeanRegistrationException {
            return null;
        }


        @Override
        public ObjectInstance registerMBean(Object object, ObjectName name)
                throws InstanceAlreadyExistsException, MBeanRegistrationException,
                NotCompliantMBeanException {
            return null;
        }


        @Override
        public void unregisterMBean(ObjectName name)
                throws InstanceNotFoundException, MBeanRegistrationException {

        }


        @Override
        public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException {
            return null;
        }


        @Override
        public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
            return Collections.emptySet();
        }


        @Override
        public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
            return Collections.emptySet();
        }


        @Override
        public boolean isRegistered(ObjectName name) {
            return false;
        }


        @Override
        public Integer getMBeanCount() {
            return null;
        }


        @Override
        public Object getAttribute(ObjectName name, String attribute) throws MBeanException,
                AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
            return null;
        }


        @Override
        public AttributeList getAttributes(ObjectName name, String[] attributes)
                throws InstanceNotFoundException, ReflectionException {
            return null;
        }


        @Override
        public void setAttribute(ObjectName name, Attribute attribute)
                throws InstanceNotFoundException, AttributeNotFoundException,
                InvalidAttributeValueException, MBeanException, ReflectionException {

        }


        @Override
        public AttributeList setAttributes(ObjectName name, AttributeList attributes)
                throws InstanceNotFoundException, ReflectionException {
            return null;
        }


        @Override
        public Object invoke(ObjectName name, String operationName, Object[] params,
                String[] signature)
                throws InstanceNotFoundException, MBeanException, ReflectionException {
            return null;
        }


        @Override
        public String getDefaultDomain() {
            return null;
        }


        @Override
        public String[] getDomains() {
            return new String[0];
        }


        @Override
        public void addNotificationListener(ObjectName name, NotificationListener listener,
                NotificationFilter filter, Object handback) throws InstanceNotFoundException {

        }


        @Override
        public void addNotificationListener(ObjectName name, ObjectName listener,
                NotificationFilter filter, Object handback) throws InstanceNotFoundException {

        }


        @Override
        public void removeNotificationListener(ObjectName name, ObjectName listener)
                throws InstanceNotFoundException, ListenerNotFoundException {

        }


        @Override
        public void removeNotificationListener(ObjectName name, ObjectName listener,
                NotificationFilter filter, Object handback)
                throws InstanceNotFoundException, ListenerNotFoundException {

        }


        @Override
        public void removeNotificationListener(ObjectName name, NotificationListener listener)
                throws InstanceNotFoundException, ListenerNotFoundException {

        }


        @Override
        public void removeNotificationListener(ObjectName name, NotificationListener listener,
                NotificationFilter filter, Object handback)
                throws InstanceNotFoundException, ListenerNotFoundException {

        }


        @Override
        public MBeanInfo getMBeanInfo(ObjectName name)
                throws InstanceNotFoundException, IntrospectionException, ReflectionException {
            return null;
        }


        @Override
        public boolean isInstanceOf(ObjectName name, String className)
                throws InstanceNotFoundException {
            return false;
        }


        @Override
        public Object instantiate(String className) throws ReflectionException, MBeanException {
            return null;
        }


        @Override
        public Object instantiate(String className, ObjectName loaderName)
                throws ReflectionException, MBeanException, InstanceNotFoundException {
            return null;
        }


        @Override
        public Object instantiate(String className, Object[] params, String[] signature)
                throws ReflectionException, MBeanException {
            return null;
        }


        @Override
        public Object instantiate(String className, ObjectName loaderName, Object[] params,
                String[] signature)
                throws ReflectionException, MBeanException, InstanceNotFoundException {
            return null;
        }


        @Override
        public ObjectInputStream deserialize(ObjectName name, byte[] data)
                throws InstanceNotFoundException, OperationsException {
            return null;
        }


        @Override
        public ObjectInputStream deserialize(String className, byte[] data)
                throws OperationsException, ReflectionException {
            return null;
        }


        @Override
        public ObjectInputStream deserialize(String className, ObjectName loaderName, byte[] data)
                throws InstanceNotFoundException, OperationsException, ReflectionException {
            return null;
        }


        @Override
        public ClassLoader getClassLoaderFor(ObjectName mbeanName)
                throws InstanceNotFoundException {
            return null;
        }


        @Override
        public ClassLoader getClassLoader(ObjectName loaderName) throws InstanceNotFoundException {
            return null;
        }


        @Override
        public ClassLoaderRepository getClassLoaderRepository() {
            return null;
        }
    }

    private static class PassthroughMBean extends ManagedBean {

        private static final long serialVersionUID = 1L;
    }
}