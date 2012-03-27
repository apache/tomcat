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


package org.apache.catalina.startup;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.annotation.Resource;
import javax.annotation.Resources;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.DefaultInstanceManager;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.deploy.ContextResourceEnvRef;
import org.apache.catalina.deploy.ContextService;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.MessageDestinationRef;

/**
 * <p><strong>AnnotationSet</strong> for processing the annotations of the web application
 * classes (<code>/WEB-INF/classes</code> and <code>/WEB-INF/lib</code>).</p>
 *
 * @author Fabien Carrion
 * @version $Id$
 */

public class WebAnnotationSet {

    private static final String SEPARATOR = "/";

    // --------------------------------------------------------- Public Methods


    /**
     * Process the annotations on a context.
     */
    public static void loadApplicationAnnotations(Context context) {

        loadApplicationListenerAnnotations(context);
        loadApplicationFilterAnnotations(context);
        loadApplicationServletAnnotations(context);


    }


    // -------------------------------------------------------- protected Methods


    /**
     * Process the annotations for the listeners.
     */
    protected static void loadApplicationListenerAnnotations(Context context) {
        Class<?> classClass = null;
        String[] applicationListeners = context.findApplicationListeners();
        for (int i = 0; i < applicationListeners.length; i++) {
            classClass = loadClass(context, applicationListeners[i]);
            if (classClass == null) {
                continue;
            }

            loadClassAnnotation(context, classClass);
            loadFieldsAnnotation(context, classClass);
            loadMethodsAnnotation(context, classClass);
        }
    }


    /**
     * Process the annotations for the filters.
     */
    protected static void loadApplicationFilterAnnotations(Context context) {
        Class<?> classClass = null;
        FilterDef[] filterDefs = context.findFilterDefs();
        for (int i = 0; i < filterDefs.length; i++) {
            classClass = loadClass(context, (filterDefs[i]).getFilterClass());
            if (classClass == null) {
                continue;
            }

            loadClassAnnotation(context, classClass);
            loadFieldsAnnotation(context, classClass);
            loadMethodsAnnotation(context, classClass);
        }
    }


    /**
     * Process the annotations for the servlets.
     */
    protected static void loadApplicationServletAnnotations(Context context) {

        Wrapper wrapper = null;
        Class<?> classClass = null;

        Container[] children = context.findChildren();
        for (int i = 0; i < children.length; i++) {
            if (children[i] instanceof Wrapper) {

                wrapper = (Wrapper) children[i];
                if (wrapper.getServletClass() == null) {
                    continue;
                }

                classClass = loadClass(context, wrapper.getServletClass());
                if (classClass == null) {
                    continue;
                }

                loadClassAnnotation(context, classClass);
                loadFieldsAnnotation(context, classClass);
                loadMethodsAnnotation(context, classClass);

                /* Process RunAs annotation which can be only on servlets.
                 * Ref JSR 250, equivalent to the run-as element in
                 * the deployment descriptor
                 */
                if (classClass.isAnnotationPresent(RunAs.class)) {
                    RunAs annotation = classClass.getAnnotation(RunAs.class);
                    wrapper.setRunAs(annotation.value());
                }
            }
        }


    }


    /**
     * Process the annotations on a context for a given className.
     */
    protected static void loadClassAnnotation(Context context,
            Class<?> classClass) {
        // Initialize the annotations
        if (classClass.isAnnotationPresent(Resource.class)) {
            Resource annotation = classClass.getAnnotation(Resource.class);
            addResource(context, annotation);
        }
        /* Process Resources annotation.
         * Ref JSR 250
         */
        if (classClass.isAnnotationPresent(Resources.class)) {
            Resources annotation = classClass.getAnnotation(Resources.class);
            for (int i = 0; annotation.value() != null && i < annotation.value().length; i++) {
                addResource(context, annotation.value()[i]);
            }
        }
        /* Process EJB annotation.
         * Ref JSR 224, equivalent to the ejb-ref or ejb-local-ref
         * element in the deployment descriptor.
        if (classClass.isAnnotationPresent(EJB.class)) {
            EJB annotation = (EJB)
            classClass.getAnnotation(EJB.class);

            if ((annotation.mappedName().length() == 0) ||
                    annotation.mappedName().equals("Local")) {

                ContextLocalEjb ejb = new ContextLocalEjb();

                ejb.setName(annotation.name());
                ejb.setType(annotation.beanInterface().getCanonicalName());
                ejb.setDescription(annotation.description());

                ejb.setHome(annotation.beanName());

                context.getNamingResources().addLocalEjb(ejb);

            } else if (annotation.mappedName().equals("Remote")) {

                ContextEjb ejb = new ContextEjb();

                ejb.setName(annotation.name());
                ejb.setType(annotation.beanInterface().getCanonicalName());
                ejb.setDescription(annotation.description());

                ejb.setHome(annotation.beanName());

                context.getNamingResources().addEjb(ejb);

            }

        }
         */
        /* Process WebServiceRef annotation.
         * Ref JSR 224, equivalent to the service-ref element in
         * the deployment descriptor.
         * The service-ref registration is not implemented
        if (classClass.isAnnotationPresent(WebServiceRef.class)) {
            WebServiceRef annotation = (WebServiceRef)
            classClass.getAnnotation(WebServiceRef.class);

            ContextService service = new ContextService();

            service.setName(annotation.name());
            service.setWsdlfile(annotation.wsdlLocation());

            service.setType(annotation.type().getCanonicalName());

            if (annotation.value() == null)
                service.setServiceinterface(annotation.type().getCanonicalName());

            if (annotation.type().getCanonicalName().equals("Service"))
                service.setServiceinterface(annotation.type().getCanonicalName());

            if (annotation.value().getCanonicalName().equals("Endpoint"))
                service.setServiceendpoint(annotation.type().getCanonicalName());

            service.setPortlink(annotation.type().getCanonicalName());

            context.getNamingResources().addService(service);


        }
         */
        /* Process DeclareRoles annotation.
         * Ref JSR 250, equivalent to the security-role element in
         * the deployment descriptor
         */
        if (classClass.isAnnotationPresent(DeclareRoles.class)) {
            DeclareRoles annotation =
                classClass.getAnnotation(DeclareRoles.class);
            for (int i = 0; annotation.value() != null && i < annotation.value().length; i++) {
                context.addSecurityRole(annotation.value()[i]);
            }
        }
    }


    protected static void loadFieldsAnnotation(Context context,
            Class<?> classClass) {
        // Initialize the annotations
        Field[] fields = getDeclaredFields(classClass);
        if (fields != null && fields.length > 0) {
            for (Field field : fields) {
                if (field.isAnnotationPresent(Resource.class)) {
                    Resource annotation = field.getAnnotation(Resource.class);
                    String defaultName =
                            classClass.getName() + SEPARATOR + field.getName();
                    String defaultType = field.getType().getCanonicalName();
                    addResource(context, annotation, defaultName, defaultType);
                }
            }
        }
    }


    protected static void loadMethodsAnnotation(Context context,
            Class<?> classClass) {
        // Initialize the annotations
        Method[] methods = getDeclaredMethods(classClass);
        if (methods != null && methods.length > 0) {
            for (Method method : methods) {
                if (method.isAnnotationPresent(Resource.class)) {
                    Resource annotation = method.getAnnotation(Resource.class);

                    checkBeanNamingConventions(method);

                    String defaultName = classClass.getName() + SEPARATOR +
                            DefaultInstanceManager.getName(method);

                    String defaultType =
                            (method.getParameterTypes()[0]).getCanonicalName();
                    addResource(context, annotation, defaultName, defaultType);
                }
            }
        }
    }

    /**
     * Process a Resource annotation to set up a Resource.
     * Ref JSR 250, equivalent to the resource-ref,
     * message-destination-ref, env-ref, resource-env-ref
     * or service-ref element in the deployment descriptor.
     */
    protected static void addResource(Context context, Resource annotation) {
        addResource(context, annotation, null, null);
    }

    protected static void addResource(Context context, Resource annotation,
            String defaultName, String defaultType) {
        String name = getName(annotation, defaultName);
        String type = getType(annotation, defaultType);

        if (type.equals("java.lang.String") ||
                type.equals("java.lang.Character") ||
                type.equals("java.lang.Integer") ||
                type.equals("java.lang.Boolean") ||
                type.equals("java.lang.Double") ||
                type.equals("java.lang.Byte") ||
                type.equals("java.lang.Short") ||
                type.equals("java.lang.Long") ||
                type.equals("java.lang.Float")) {

            // env-ref element
            ContextEnvironment resource = new ContextEnvironment();

            resource.setName(name);
            resource.setType(type);

            resource.setDescription(annotation.description());

            resource.setValue(annotation.mappedName());

            context.getNamingResources().addEnvironment(resource);

        } else if (type.equals("javax.xml.rpc.Service")) {

            // service-ref element
            ContextService service = new ContextService();

            service.setName(name);
            service.setWsdlfile(annotation.mappedName());

            service.setType(type);
            service.setDescription(annotation.description());

            context.getNamingResources().addService(service);

        } else if (type.equals("javax.sql.DataSource") ||
                type.equals("javax.jms.ConnectionFactory") ||
                type.equals("javax.jms.QueueConnectionFactory") ||
                type.equals("javax.jms.TopicConnectionFactory") ||
                type.equals("javax.mail.Session") ||
                type.equals("java.net.URL") ||
                type.equals("javax.resource.cci.ConnectionFactory") ||
                type.equals("org.omg.CORBA_2_3.ORB") ||
                type.endsWith("ConnectionFactory")) {

            // resource-ref element
            ContextResource resource = new ContextResource();

            resource.setName(name);
            resource.setType(type);

            if (annotation.authenticationType()
                    == Resource.AuthenticationType.CONTAINER) {
                resource.setAuth("Container");
            }
            else if (annotation.authenticationType()
                    == Resource.AuthenticationType.APPLICATION) {
                resource.setAuth("Application");
            }

            resource.setScope(annotation.shareable() ? "Shareable" : "Unshareable");
            resource.setProperty("mappedName", annotation.mappedName());
            resource.setDescription(annotation.description());

            context.getNamingResources().addResource(resource);

        } else if (type.equals("javax.jms.Queue") ||
                type.equals("javax.jms.Topic")) {

            // message-destination-ref
            MessageDestinationRef resource = new MessageDestinationRef();

            resource.setName(name);
            resource.setType(type);

            resource.setUsage(annotation.mappedName());
            resource.setDescription(annotation.description());

            context.getNamingResources().addMessageDestinationRef(resource);

        } else if (type.equals("javax.resource.cci.InteractionSpec") ||
                type.equals("javax.transaction.UserTransaction") ||
                true) {

            // resource-env-ref
            ContextResourceEnvRef resource = new ContextResourceEnvRef();

            resource.setName(name);
            resource.setType(type);

            resource.setProperty("mappedName", annotation.mappedName());
            resource.setDescription(annotation.description());

            context.getNamingResources().addResourceEnvRef(resource);

        }
    }


    private static void checkBeanNamingConventions(Method method) {
        if (!method.getName().startsWith("set")
                || method.getName().length() < 4
                || method.getParameterTypes().length != 1
                || !method.getReturnType().getName().equals("void")) {
            throw new IllegalArgumentException("Invalid method resource injection annotation.");
        }
    }


    private static String getType(Resource annotation, String defaultType) {
        String type = annotation.type().getCanonicalName();
        if (type == null || type.equals("java.lang.Object")) {
            if (defaultType != null) {
                type = defaultType;
            }
        }
        return type;
    }


    private static String getName(Resource annotation, String defaultName) {
        String name = annotation.name();
        if (name == null || name.equals("")) {
            if (defaultName != null) {
                name = defaultName;
            }
        }
        return name;
    }


    private static Field[] getDeclaredFields(Class<?> classClass) {
        Field[] fields = null;
        if (Globals.IS_SECURITY_ENABLED) {
            final Class<?> clazz = classClass;
            fields = AccessController.doPrivileged(
                    new PrivilegedAction<Field[]>(){
                @Override
                public Field[] run(){
                    return clazz.getDeclaredFields();
                }
            });
        } else {
            fields = classClass.getDeclaredFields();
        }
        return fields;
    }


    private static Method[] getDeclaredMethods(Class<?> classClass) {
        Method[] methods = null;
        if (Globals.IS_SECURITY_ENABLED) {
            final Class<?> clazz = classClass;
            methods = AccessController.doPrivileged(
                    new PrivilegedAction<Method[]>(){
                @Override
                public Method[] run(){
                    return clazz.getDeclaredMethods();
                }
            });
        } else {
            methods = classClass.getDeclaredMethods();
        }
        return methods;
    }


    private static Class<?> loadClass(Context context, String fileString) {
        ClassLoader classLoader = context.getLoader().getClassLoader();
        Class<?> classClass = null;
        try {
            classClass = classLoader.loadClass(fileString);
        } catch (ClassNotFoundException e) {
            // We do nothing
        } catch (NoClassDefFoundError e) {
            // We do nothing
        }
        return classClass;
    }
}
