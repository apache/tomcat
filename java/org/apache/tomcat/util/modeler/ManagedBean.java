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


/**
 * <p>Internal configuration information for a managed bean (MBean)
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 */
public class ManagedBean implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private static final String BASE_MBEAN = "org.apache.tomcat.util.modeler.BaseModelMBean";
    // ----------------------------------------------------- Instance Variables
    static final Class<?>[] NO_ARGS_PARAM_SIG = new Class[0];


    private final ReadWriteLock mBeanInfoLock = new ReentrantReadWriteLock();
    /**
     * The <code>ModelMBeanInfo</code> object that corresponds
     * to this <code>ManagedBean</code> instance.
     */
    private transient volatile MBeanInfo info = null;

    private Map<String,AttributeInfo> attributes = new HashMap<>();

    private Map<String,OperationInfo> operations = new HashMap<>();

    protected String className = BASE_MBEAN;
    protected String description = null;
    protected String domain = null;
    protected String group = null;
    protected String name = null;

    private NotificationInfo notifications[] = new NotificationInfo[0];
    protected String type = null;

    /** Constructor. Will add default attributes.
     *
     */
    public ManagedBean() {
        AttributeInfo ai=new AttributeInfo();
        ai.setName("modelerType");
        ai.setDescription("Type of the modeled resource. Can be set only once");
        ai.setType("java.lang.String");
        ai.setWriteable(false);
        addAttribute(ai);
    }

    // ------------------------------------------------------------- Properties


    /**
     * @return the collection of attributes for this MBean.
     */
    public AttributeInfo[] getAttributes() {
        AttributeInfo result[] = new AttributeInfo[attributes.size()];
        attributes.values().toArray(result);
        return result;
    }


    /**
     * The fully qualified name of the Java class of the MBean
     * described by this descriptor.  If not specified, the standard JMX
     * class (<code>javax.management.modelmbean.RequiredModeLMBean</code>)
     * will be utilized.
     * @return the class name
     */
    public String getClassName() {
        return this.className;
    }

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
     * @return the human-readable description of this MBean.
     */
    public String getDescription() {
        return this.description;
    }

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
     * @return the (optional) <code>ObjectName</code> domain in which
     *  this MBean should be registered in the MBeanServer.
     */
    public String getDomain() {
        return this.domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * @return the (optional) group to which this MBean belongs.
     */
    public String getGroup() {
        return this.group;
    }

    public void setGroup(String group) {
        this.group = group;
    }


    /**
     * @return the name of this managed bean, which must be unique
     *  among all MBeans managed by a particular MBeans server.
     */
    public String getName() {
        return this.name;
    }

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
     * @return the collection of notifications for this MBean.
     */
    public NotificationInfo[] getNotifications() {
        return this.notifications;
    }


    /**
     * @return the collection of operations for this MBean.
     */
    public OperationInfo[] getOperations() {
        OperationInfo[] result = new OperationInfo[operations.size()];
        operations.values().toArray(result);
        return result;
    }


    /**
     * @return the fully qualified name of the Java class of the resource
     * implementation class described by the managed bean described
     * by this descriptor.
     */
    public String getType() {
        return this.type;
    }

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
            NotificationInfo results[] =
                new NotificationInfo[notifications.length + 1];
            System.arraycopy(notifications, 0, results, 0,
                             notifications.length);
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
     * Create and return a <code>ModelMBean</code> that has been
     * preconfigured with the <code>ModelMBeanInfo</code> information
     * for this managed bean, and is associated with the specified
     * managed object instance.  The returned <code>ModelMBean</code>
     * will <strong>NOT</strong> have been registered with our
     * <code>MBeanServer</code>.
     *
     * @param instance Instanced of the managed object, or <code>null</code>
     *  for no associated instance
     * @return the MBean
     * @exception InstanceNotFoundException if the managed resource
     *  object cannot be found
     * @exception MBeanException if a problem occurs instantiating the
     *  <code>ModelMBean</code> instance
     * @exception RuntimeOperationsException if a JMX runtime error occurs
     */
    public DynamicMBean createMBean(Object instance)
        throws InstanceNotFoundException,
        MBeanException, RuntimeOperationsException {

        BaseModelMBean mbean = null;

        // Load the ModelMBean implementation class
        if(getClassName().equals(BASE_MBEAN)) {
            // Skip introspection
            mbean = new BaseModelMBean();
        } else {
            Class<?> clazz = null;
            Exception ex = null;
            try {
                clazz = Class.forName(getClassName());
            } catch (Exception e) {
            }

            if( clazz==null ) {
                try {
                    ClassLoader cl= Thread.currentThread().getContextClassLoader();
                    if ( cl != null)
                        clazz= cl.loadClass(getClassName());
                } catch (Exception e) {
                    ex=e;
                }
            }

            if( clazz==null) {
                throw new MBeanException
                    (ex, "Cannot load ModelMBean class " + getClassName());
            }
            try {
                // Stupid - this will set the default minfo first....
                mbean = (BaseModelMBean) clazz.getConstructor().newInstance();
            } catch (RuntimeOperationsException e) {
                throw e;
            } catch (Exception e) {
                throw new MBeanException
                    (e, "Cannot instantiate ModelMBean of class " +
                     getClassName());
            }
        }

        mbean.setManagedBean(this);

        // Set the managed resource (if any)
        try {
            if (instance != null)
                mbean.setManagedResource(instance, "ObjectReference");
        } catch (InstanceNotFoundException e) {
            throw e;
        }

        return mbean;
    }


    /**
     * Create and return a <code>ModelMBeanInfo</code> object that
     * describes this entire managed bean.
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
                AttributeInfo attrs[] = getAttributes();
                MBeanAttributeInfo attributes[] =
                    new MBeanAttributeInfo[attrs.length];
                for (int i = 0; i < attrs.length; i++)
                    attributes[i] = attrs[i].createAttributeInfo();

                OperationInfo opers[] = getOperations();
                MBeanOperationInfo operations[] =
                    new MBeanOperationInfo[opers.length];
                for (int i = 0; i < opers.length; i++)
                    operations[i] = opers[i].createOperationInfo();


                NotificationInfo notifs[] = getNotifications();
                MBeanNotificationInfo notifications[] =
                    new MBeanNotificationInfo[notifs.length];
                for (int i = 0; i < notifs.length; i++)
                    notifications[i] = notifs[i].createNotificationInfo();


                // Construct and return a new ModelMBeanInfo object
                info = new MBeanInfo(getClassName(),
                                     getDescription(),
                                     attributes,
                                     new MBeanConstructorInfo[] {},
                                     operations,
                                     notifications);
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
        sb.append("]");
        return sb.toString();

    }

    Method getGetter(String aname, BaseModelMBean mbean, Object resource)
            throws AttributeNotFoundException, ReflectionException {

        Method m = null;

        AttributeInfo attrInfo = attributes.get(aname);
        // Look up the actual operation to be used
        if (attrInfo == null)
            throw new AttributeNotFoundException(" Cannot find attribute " + aname + " for " + resource);

        String getMethod = attrInfo.getGetMethod();

        Object object = null;
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
                exception=null;
            } catch (NoSuchMethodException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw new ReflectionException(exception,
                                          "Cannot find getter method " + getMethod);
        }

        return m;
    }

    public Method getSetter(String aname, BaseModelMBean bean, Object resource)
            throws AttributeNotFoundException, ReflectionException {

        Method m = null;

        AttributeInfo attrInfo = attributes.get(aname);
        if (attrInfo == null) {
            throw new AttributeNotFoundException(" Cannot find attribute " + aname);
        }

        // Look up the actual operation to be used
        String setMethod = attrInfo.getSetMethod();
        String argType=attrInfo.getType();

        Class<?> signature[] = new Class[] { BaseModelMBean.getAttributeClass( argType ) };

        Object object = null;
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
                exception=null;
            } catch (NoSuchMethodException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw new ReflectionException(exception,
                    "Cannot find setter method " + setMethod + " " + resource);
        }

        return m;
    }

    public Method getInvoke(String aname, Object[] params, String[] signature, BaseModelMBean bean, Object resource)
            throws MBeanException, ReflectionException {

        Method method = null;

        if (params == null)
            params = new Object[0];
        if (signature == null)
            signature = new String[0];
        if (params.length != signature.length)
            throw new RuntimeOperationsException(
                    new IllegalArgumentException(
                            "Inconsistent arguments and signature"),
                    "Inconsistent arguments and signature");

        // Acquire the ModelMBeanOperationInfo information for
        // the requested operation
        OperationInfo opInfo =
                operations.get(createOperationKey(aname, signature));
        if (opInfo == null)
            throw new MBeanException(new ServiceNotFoundException(
                    "Cannot find operation " + aname),
                    "Cannot find operation " + aname);

        // Prepare the signature required by Java reflection APIs
        // FIXME - should we use the signature from opInfo?
        Class<?> types[] = new Class[signature.length];
        for (int i = 0; i < signature.length; i++) {
            types[i] = BaseModelMBean.getAttributeClass(signature[i]);
        }

        // Locate the method to be invoked, either in this MBean itself
        // or in the corresponding managed resource
        // FIXME - Accessible methods in superinterfaces?
        Object object = null;
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
            throw new ReflectionException(exception, "Cannot find method "
                    + aname + " with this signature");
        }

        return method;
    }


    private String createOperationKey(OperationInfo operation) {
        StringBuilder key = new StringBuilder(operation.getName());
        key.append('(');
        StringUtils.join(operation.getSignature(), ',', (x) -> x.getType(), key);
        key.append(')');

        return key.toString();
    }


    private String createOperationKey(String methodName, String[] parameterTypes) {
        StringBuilder key = new StringBuilder(methodName);
        key.append('(');
        StringUtils.join(parameterTypes, ',', key);
        key.append(')');

        return key.toString();
    }
}
