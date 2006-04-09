/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.catalina.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.naming.NamingException;

import org.apache.tomcat.util.IntrospectionUtils;


/**
 * Verify the annotation and Process it.
 *
 * @author    Fabien Carrion
 * @version   $Revision: 303236 $, $Date: 2006-03-09 16:46:52 -0600 (Thu, 09 Mar 2006) $
 */
public class AnnotationProcessor {
    

    /**
     * Call postConstruct method on the specified instance.
     */
    public static void postConstruct(Object instance)
        throws IllegalAccessException, InvocationTargetException {
        
        Method[] methods = IntrospectionUtils.findMethods(instance.getClass());
        Method postConstruct = null;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].isAnnotationPresent(PostConstruct.class)) {
                if ((postConstruct != null) 
                        || (methods[i].getParameterTypes().length != 0)
                        || (Modifier.isStatic(methods[i].getModifiers())) 
                        || (methods[i].getExceptionTypes().length > 0)
                        || (!methods[i].getReturnType().getName().equals("void"))) {
                    throw new IllegalArgumentException("Invalid PostConstruct annotation");
                }
                postConstruct = methods[i];
            }
        }

        // At the end the postconstruct annotated 
        // method is invoked
        if (postConstruct != null) {
            boolean accessibility = postConstruct.isAccessible();
            postConstruct.setAccessible(true);
            postConstruct.invoke(instance);
            postConstruct.setAccessible(accessibility);
        }
        
    }
    
    
    /**
     * Call preDestroy method on the specified instance.
     */
    public static void preDestroy(Object instance)
        throws IllegalAccessException, InvocationTargetException {
        
        Method[] methods = IntrospectionUtils.findMethods(instance.getClass());
        Method preDestroy = null;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].isAnnotationPresent(PostConstruct.class)) {
                if ((preDestroy != null) 
                        || (methods[i].getParameterTypes().length != 0)
                        || (Modifier.isStatic(methods[i].getModifiers())) 
                        || (methods[i].getExceptionTypes().length > 0)
                        || (!methods[i].getReturnType().getName().equals("void"))) {
                    throw new IllegalArgumentException("Invalid PreDestroy annotation");
                }
                preDestroy = methods[i];
            }
        }

        // At the end the postconstruct annotated 
        // method is invoked
        if (preDestroy != null) {
            boolean accessibility = preDestroy.isAccessible();
            preDestroy.setAccessible(true);
            preDestroy.invoke(instance);
            preDestroy.setAccessible(accessibility);
        }
        
    }
    
    
    /**
     * Inject resources in specified instance.
     */
    public static void injectNamingResources(javax.naming.Context context, Object instance)
        throws IllegalAccessException, InvocationTargetException, NamingException {
        
        // Initialize fields annotations
        Field[] fields = instance.getClass().getFields();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].isAnnotationPresent(Resource.class)) {
                Resource annotation = (Resource) fields[i].getAnnotation(Resource.class);
                lookupFieldResource(context, instance, fields[i], annotation.name());
            }
            /*
            if (f.isAnnotationPresent(EJB.class)) {
                EJB annotation = (EJB) f.getAnnotation(EJB.class);
                lookupOnFieldResource(f, annotation.name());
            }
            
            if (f.isAnnotationPresent(WebServiceRef.class)) {
                WebServiceRef annotation = (WebServiceRef) 
                f.getAnnotation(WebServiceRef.class);
                lookupOnFieldResource(f, annotation.name());
            }
            */
        }
        
        // Initialize methods annotations
        Method[] methods = IntrospectionUtils.findMethods(instance.getClass());
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].isAnnotationPresent(Resource.class)) {
                Resource annotation = (Resource) methods[i].getAnnotation(Resource.class);
                lookupMethodResource(context, instance, methods[i], annotation.name());
            }
            /*
            if (m.isAnnotationPresent(EJB.class)) {
                EJB annotation = (EJB) m.getAnnotation(EJB.class);
                lookupOnMethodResource(m, annotation.name());
            }
            if (m.isAnnotationPresent(WebServiceRef.class)) {
                WebServiceRef annotation = (WebServiceRef) 
                m.getAnnotation(WebServiceRef.class);
                lookupOnMethodResource(m, annotation.name());
            }
            */
        }            

    }
    
    
    protected static void lookupFieldResource(javax.naming.Context context, 
            Object instance, Field f, String name)
        throws NamingException, IllegalAccessException {
    
        Object lookedupResource = null;
        boolean accessibility = false;
        
        if ((name != null) &&
                (name.length() > 0)) {
            lookedupResource = context.lookup(name);
        } else {
            lookedupResource = context.lookup(instance.getClass().getName() + "/" + f.getName());
        }
        
        accessibility = f.isAccessible();
        f.setAccessible(true);
        f.set(instance, lookedupResource);
        f.setAccessible(accessibility);
    }


    protected static void lookupMethodResource(javax.naming.Context context, 
            Object instance, Method method, String name)
        throws NamingException, IllegalAccessException, InvocationTargetException {
        
        if (!method.getName().startsWith("set") 
                || method.getParameterTypes().length != 1
                || !method.getReturnType().getName().equals("void")) {
            throw new IllegalArgumentException("Invalid method resource injection annotation");
        }
        
        Object lookedupResource = null;
        boolean accessibility = false;
        
        if ((name != null) &&
                (name.length() > 0)) {
            lookedupResource = context.lookup(name);
        } else {
            lookedupResource = 
                context.lookup(instance.getClass().getName() + "/" + method.getName().substring(3));
        }
        
        accessibility = method.isAccessible();
        method.setAccessible(true);
        method.invoke(instance, lookedupResource);
        method.setAccessible(accessibility);
    }
    

}
