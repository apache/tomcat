/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.web.tomcat;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.NamingException;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Producer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.res.StringManager;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.inject.OWBInjector;

public class OpenWebBeansInstanceManager implements InstanceManager {

    private static final Log log = LogFactory.getLog(OpenWebBeansInstanceManager.class);
    private static final StringManager sm = StringManager.getManager(OpenWebBeansInstanceManager.class);

    private final ClassLoader loader;
    private final InstanceManager instanceManager;
    private final Map<Object, Instance> instances = new ConcurrentHashMap<>();
    private static final class Instance {
        private final Object object;
        private final CreationalContext<?> context;
        private Instance(Object object, CreationalContext<?> context) {
            this.object = object;
            this.context = context;
        }
    }

    public OpenWebBeansInstanceManager(ClassLoader loader, InstanceManager instanceManager) {
        this.loader = loader;
        this.instanceManager = instanceManager;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void destroyInstance(Object object)
            throws IllegalAccessException, InvocationTargetException {
        Instance injectorInstance = instances.get(object);
        if (injectorInstance != null) {
            try {
                ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(loader);
                try {
                    BeanManagerImpl beanManager = WebBeansContext.currentInstance().getBeanManagerImpl();
                    @SuppressWarnings("rawtypes")
                    Producer producer = beanManager.getProducerForJavaEeComponent(injectorInstance.object.getClass());
                    if (producer != null) {
                        producer.dispose(injectorInstance.object);
                    } else if (injectorInstance.context != null) {
                        injectorInstance.context.release();
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader(oldLoader);
                }
            } catch (Exception e) {
                log.error(sm.getString("instanceManager.destroyError", object), e);
            }
        }
        this.instanceManager.destroyInstance(object);
    }

    @Override
    public Object newInstance(Class<?> aClass) throws IllegalAccessException,
            InvocationTargetException, NamingException, InstantiationException,
            IllegalArgumentException, NoSuchMethodException, SecurityException {
        Object object = this.instanceManager.newInstance(aClass);
        inject(object);
        return object;
    }

    @Override
    public Object newInstance(String str)
            throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException, ClassNotFoundException,
            IllegalArgumentException, NoSuchMethodException, SecurityException {
        Object object = this.instanceManager.newInstance(str);
        inject(object);
        return object;
    }

    @Override
    public void newInstance(Object object) throws IllegalAccessException,
            InvocationTargetException, NamingException {
        inject(object);
    }

    @Override
    public Object newInstance(String str, ClassLoader cl)
            throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException, ClassNotFoundException,
            IllegalArgumentException, NoSuchMethodException, SecurityException {
        Object object = this.instanceManager.newInstance(str, cl);
        inject(object);
        return object;
    }

    private void inject(Object object) {
        try {
            ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            CreationalContext<?> context = null;
            try {
                BeanManager beanManager = WebBeansContext.currentInstance().getBeanManagerImpl();
                context = beanManager.createCreationalContext(null);
                OWBInjector.inject(beanManager, object, context);
            } finally {
                Thread.currentThread().setContextClassLoader(oldLoader);
            }
            instances.put(object, new Instance(object, context));
        } catch (Exception e) {
            log.error(sm.getString("instanceManager.injectError", object), e);
        }
    }

}
