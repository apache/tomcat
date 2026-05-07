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


import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.ServiceNotFoundException;

import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.res.StringManager;


/**
 * <p>
 * Internal configuration information for a managed bean (MBean) descriptor.
 * </p>
 */
public class ManagedBean implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    private static final StringManager sm = StringManager.getManager(ManagedBean.class);

    private static final String BASE_MBEAN = "org.apache.tomcat.util.modeler.BaseModelMBean";
    // ----------------------------------------------------- Instance Variables
    static final Class<?>[] NO_ARGS_PARAM_SIG = new Class[0];


    private final ReadWriteLock mBeanInfoLock = new ReentrantReadWriteLock();
    /**
     * The <code>ModelMBeanInfo</code> object that corresponds to this <code>ManagedBean</code> instance.
     */
    private transient volatile MBeanInfo info = null;

    private final Map<String,AttributeInfo> attributes = new HashMap<>();

    private final Map<String,OperationInfo> operations = new HashMap<>();

    /**
     * The fully qualified name of the Java class of the MBean.
     */
    protected String className = BASE_MBEAN;
    /**
     * The human-readable description of this MBean.
     */
    protected String description = null;
    /**
     * The ObjectName domain in which this MBean should be registered.
     */
    protected String domain = null;
    /**
     * The group to which this MBean belongs.
     */
    protected String group = null;
    /**
     * The name of this managed bean.
     */
    protected String name = null;

    /**
     * The collection of notifications for this MBean.
     */
    private NotificationInfo[] notifications = new NotificationInfo[0];
    /**
     * The fully qualified name of the Java class of the resource implementation class.
     */
    protected String type = null;

    /**
     * Constructor. Will add default attributes.
     */
    public ManagedBean() {
        AttributeInfo ai = new AttributeInfo();
        ai.setName("modelerType");
        ai.setDescription("Type of the modeled resource. Can be set only once");
        ai.setType("java.lang.String");
        ai.setWriteable(false);
        addAttribute(ai);
    }

    // ------------------------------------------------------------- Properties


    /**
     * Return the collection of attributes for this MBean.
     *
     * @return the collection of attributes for this MBean
     */
    public AttributeInfo[] getAttributes() {
        return attributes.values().toArray(new AttributeInfo[0]);
    }


    /**
     * The fully qualified name of the Java class of the MBean described by this descriptor. If not specified, the
     * standard JMX class (<code>javax.management.modelmbean.RequiredModeLMBean</code>) will be utilized.
     *
     * @return the class name
     */
    public String getClassName() {
        return this.className;
    }

    /**
     * Set the fully qualified name of the Java class of the MBean.
     *
     * @param className the className to set
     */
    public void setClassName(String className) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.className = className;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * Return the human-readable description of this MBean.
     *
     * @return the human-readable description of this MBean
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Set the human-readable description of this MBean.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.description = description;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * Return the (optional) <code>ObjectName</code> domain in which this MBean should be registered in the
     * MBeanServer.
     *
     * @return the (optional) <code>ObjectName</code> domain in which this MBean should be registered
     */
    public String getDomain() {
        return this.domain;
    }

    /**
     * Set the (optional) <code>ObjectName</code> domain in which this MBean should be registered.
     *
     * @param domain the domain to set
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * Return the (optional) group to which this MBean belongs.
     *
     * @return the (optional) group to which this MBean belongs
     */
    public String getGroup() {
        return this.group;
    }

    /**
     * Set the (optional) group to which this MBean belongs.
     *
     * @param group the group to set
     */
    public void setGroup(String group) {
        this.group = group;
    }


    /**
     * Return the name of this managed bean, which must be unique among all MBeans managed by a particular MBeans
     * server.
     *
     * @return the name of this managed bean
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the name of this managed bean.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.name = name;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * Return the collection of notifications for this MBean.
     *
     * @return the collection of notifications for this MBean
     */
    public NotificationInfo[] getNotifications() {
        return this.notifications;
    }


    /**
     * Return the collection of operations for this MBean.
     *
     * @return the collection of operations for this MBean
     */
    public OperationInfo[] getOperations() {
        return operations.values().toArray(new OperationInfo[0]);
    }


    /**
     * Return the fully qualified name of the Java class of the resource implementation class described by the managed
     * bean described by this descriptor.
     *
     * @return the fully qualified name of the Java class of the resource implementation class
     */
    public String getType() {
        return this.type;
    }

    /**
     * Set the fully qualified name of the Java class of the resource implementation class.
     *
     * @param type the type to set
     */
    public void setType(String type) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.type = type;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new attribute to the set of attributes for this MBean.
     *
     * @param attribute The new attribute descriptor
     */
    public void addAttribute(AttributeInfo attribute) {
        attributes.put(attribute.getName(), attribute);
    }


    /**
     * Add a new notification to the set of notifications for this MBean.
     *
     * @param notification The new notification descriptor
     */
    public void addNotification(NotificationInfo notification) {
        mBeanInfoLock.writeLock().lock();
        try {
            NotificationInfo[] results = new NotificationInfo[notifications.length + 1];
            System.arraycopy(notifications, 0, results, 0, notifications.length);
            results[notifications.length] = notification;
            notifications = results;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * Add a new operation to the set of operations for this MBean.
     *
     * @param operation The new operation descriptor
     */
    public void addOperation(OperationInfo operation) {
        operations.put(createOperationKey(operation), operation);
    }


    /**
     * Create and return a <code>ModelMBean</code> that has been preconfigured with the <code>ModelMBeanInfo</code>
     * information for this managed bean, and is associated with the specified managed object instance. The returned
     * <code>ModelMBean</code> will <strong>NOT</strong> have been registered with our <code>MBeanServer</code>.
     *
     * @param instance Instanced of the managed object, or <code>null</code> for no associated instance
     *
     * @return the MBean
     *
     * @exception InstanceNotFoundException  if the managed resource object cannot be found
     * @exception MBeanException             if a problem occurs instantiating the <code>ModelMBean</code> instance
     * @exception RuntimeOperationsException if a JMX runtime error occurs
     */
    public DynamicMBean createMBean(Object instance)
            throws InstanceNotFoundException, MBeanException, RuntimeOperationsException {

        BaseModelMBean mbean;

        // Load the ModelMBean implementation class
        if (getClassName().equals(BASE_MBEAN)) {
            // Skip introspection
            mbean = new BaseModelMBean();
        } else {
            Class<?> clazz = null;
            Exception ex = null;
            try {
                clazz = Class.forName(getClassName());
            } catch (Exception e) {
                // Ignore
            }

            if (clazz == null) {
                try {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    if (cl != null) {
                        clazz = cl.loadClass(getClassName());
                    }
                } catch (Exception e) {
                    ex = e;
                }
            }

            if (clazz == null) {
                throw new MBeanException(ex, sm.getString("managedMBean.cannotLoadClass", getClassName()));
            }
            try {
                // Stupid - this will set the default minfo first....
                mbean = (BaseModelMBean) clazz.getConstructor().newInstance();
            } catch (RuntimeOperationsException e) {
                throw e;
            } catch (Exception e) {
                throw new MBeanException(e, sm.getString("managedMBean.cannotInstantiateClass", getClassName()));
            }
        }

        mbean.setManagedBean(this);

        // Set the managed resource (if any)
        if (instance != null) {
            mbean.setManagedResource(instance, "ObjectReference");
        }

        return mbean;
    }


    /**
     * Create and return a <code>ModelMBeanInfo</code> object that describes this entire managed bean.
     *
     * @return the MBean info
     */
    MBeanInfo getMBeanInfo() {

        // Return our cached information (if any)
        mBeanInfoLock.readLock().lock();
        try {
            if (info != null) {
                return info;
            }
        } finally {
            mBeanInfoLock.readLock().unlock();
        }

        mBeanInfoLock.writeLock().lock();
        try {
            if (info == null) {
                // Create subordinate information descriptors as required
                AttributeInfo[] attrs = getAttributes();
                MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[attrs.length];
                for (int i = 0; i < attrs.length; i++) {
                    attributes[i] = attrs[i].createAttributeInfo();
                }

                OperationInfo[] opers = getOperations();
                MBeanOperationInfo[] operations = new MBeanOperationInfo[opers.length];
                for (int i = 0; i < opers.length; i++) {
                    operations[i] = opers[i].createOperationInfo();
                }


                NotificationInfo[] notifs = getNotifications();
                MBeanNotificationInfo[] notifications = new MBeanNotificationInfo[notifs.length];
                for (int i = 0; i < notifs.length; i++) {
                    notifications[i] = notifs[i].createNotificationInfo();
                }


                // Construct and return a new ModelMBeanInfo object
                info = new MBeanInfo(getClassName(), getDescription(), attributes, new MBeanConstructorInfo[] {},
                        operations, notifications);
            }

            return info;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * Return a string representation of this managed bean.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ManagedBean[");
        sb.append("name=");
        sb.append(name);
        sb.append(", className=");
        sb.append(className);
        sb.append(", description=");
        sb.append(description);
        if (group != null) {
            sb.append(", group=");
            sb.append(group);
        }
        sb.append(", type=");
        sb.append(type);
        sb.append(']');
        return sb.toString();

    }

    Method getGetter(String aname, BaseModelMBean mbean, Object resource)
            throws AttributeNotFoundException, ReflectionException {

        Method m = null;

        AttributeInfo attrInfo = attributes.get(aname);
        // Look up the actual operation to be used
        if (attrInfo == null) {
            throw new AttributeNotFoundException(sm.getString("managedMBean.noAttribute", aname, resource));
        }

        String getMethod = attrInfo.getGetMethod();

        Object object;
        NoSuchMethodException exception = null;
        try {
            object = mbean;
            m = object.getClass().getMethod(getMethod, NO_ARGS_PARAM_SIG);
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        if (m == null && resource != null) {
            try {
                object = resource;
                m = object.getClass().getMethod(getMethod, NO_ARGS_PARAM_SIG);
                exception = null;
            } catch (NoSuchMethodException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw new ReflectionException(exception, sm.getString("managedMBean.noGet", getMethod, resource));
        }

        return m;
    }

    /**
     * Get the setter method for the given attribute.
     *
     * @param aname The attribute name
     * @param bean The MBean
     * @param resource The resource
     * @return the setter method
     * @throws AttributeNotFoundException if the attribute is not found
     * @throws ReflectionException if a reflection error occurs
     */
    public Method getSetter(String aname, BaseModelMBean bean, Object resource)
            throws AttributeNotFoundException, ReflectionException {

        Method m = null;

        AttributeInfo attrInfo = attributes.get(aname);
        if (attrInfo == null) {
            throw new AttributeNotFoundException(sm.getString("managedMBean.noAttribute", aname, resource));
        }

        // Look up the actual operation to be used
        String setMethod = attrInfo.getSetMethod();
        String argType = attrInfo.getType();

        Class<?>[] signature = new Class[] { BaseModelMBean.getAttributeClass(argType) };

        Object object;
        NoSuchMethodException exception = null;
        try {
            object = bean;
            m = object.getClass().getMethod(setMethod, signature);
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        if (m == null && resource != null) {
            try {
                object = resource;
                m = object.getClass().getMethod(setMethod, signature);
                exception = null;
            } catch (NoSuchMethodException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw new ReflectionException(exception, sm.getString("managedMBean.noSet", setMethod, resource));
        }

        return m;
    }

    /**
     * Get the method to invoke for the given operation.
     *
     * @param aname The operation name
     * @param params The parameters
     * @param signature The parameter signature
     * @param bean The MBean
     * @param resource The resource
     * @return the method to invoke
     * @throws MBeanException if the operation is not found
     * @throws ReflectionException if a reflection error occurs
     */
    public Method getInvoke(String aname, Object[] params, String[] signature, BaseModelMBean bean, Object resource)
            throws MBeanException, ReflectionException {

        Method method = null;

        if (params == null) {
            params = new Object[0];
        }
        if (signature == null) {
            signature = new String[0];
        }
        if (params.length != signature.length) {
            throw new RuntimeOperationsException(
                    new IllegalArgumentException(sm.getString("managedMBean.inconsistentArguments")),
                    sm.getString("managedMBean.inconsistentArguments"));
        }

        // Acquire the ModelMBeanOperationInfo information for
        // the requested operation
        OperationInfo opInfo = operations.get(createOperationKey(aname, signature));
        if (opInfo == null) {
            throw new MBeanException(new ServiceNotFoundException(sm.getString("managedMBean.noOperation", aname)),
                    sm.getString("managedMBean.noOperation", aname));
        }

        // Prepare the signature required by Java reflection APIs
        // FIXME - should we use the signature from opInfo?
        Class<?>[] types = new Class[signature.length];
        for (int i = 0; i < signature.length; i++) {
            types[i] = BaseModelMBean.getAttributeClass(signature[i]);
        }

        // Locate the method to be invoked, either in this MBean itself
        // or in the corresponding managed resource
        // FIXME - Accessible methods in superinterfaces?
        Object object;
        Exception exception = null;
        try {
            object = bean;
            method = object.getClass().getMethod(aname, types);
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        try {
            if ((method == null) && (resource != null)) {
                object = resource;
                method = object.getClass().getMethod(aname, types);
            }
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        if (method == null) {
            throw new ReflectionException(exception, sm.getString("managedMBean.noMethod", aname));
        }

        return method;
    }


    private String createOperationKey(OperationInfo operation) {
        StringBuilder key = new StringBuilder(operation.getName());
        key.append('(');
        StringUtils.join(operation.getSignature(), ',', FeatureInfo::getType, key);
        key.append(')');

        return key.toString().intern();
    }


    private String createOperationKey(String methodName, String[] parameterTypes) {
        StringBuilder key = new StringBuilder(methodName);
        key.append('(');
        StringUtils.join(parameterTypes, ',', key);
        key.append(')');

        return key.toString().intern();
    }
}
