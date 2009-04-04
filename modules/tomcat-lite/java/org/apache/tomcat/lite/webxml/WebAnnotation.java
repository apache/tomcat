package org.apache.tomcat.lite.webxml;

import java.util.Iterator;
import java.util.List;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;

import org.apache.tomcat.lite.ServletContextConfig;
import org.apache.tomcat.lite.ServletContextConfig.FilterData;
import org.apache.tomcat.lite.ServletContextConfig.ServletData;




/**
 * Based on catalina.WebAnnotationSet
 * 
 * Supports:
 *  @DeclaresRoles - on Servlet class - web-app/security-role/role-name
 *  @RunAs - on Servlet class - web-app/servlet/run-as
 *  
 * 
 * No support for jndi @Resources, @Resource
 * No @InjectionComplete callback annotation
 * 
 * No support for @EJB, @WebServiceRef
 *  
 * @author costin
 * @author Fabien Carrion
 */
public class WebAnnotation {

    /**
     * Process the annotations on a context.
     */
    public static void loadApplicationAnnotations(ServletContextConfig context, ClassLoader classLoader) {
        loadApplicationListenerAnnotations(context, classLoader);
        loadApplicationFilterAnnotations(context, classLoader);
        loadApplicationServletAnnotations(context, classLoader);
    }
    
    
    // -------------------------------------------------------- protected Methods
    
    
    /**
     * Process the annotations for the listeners.
     */
    static void loadApplicationListenerAnnotations(ServletContextConfig context, ClassLoader classLoader) {
        List applicationListeners = context.listenerClass;
        for (int i = 0; i < applicationListeners.size(); i++) {
            loadClassAnnotation(context, (String)applicationListeners.get(i), classLoader);
        }
    }
    
    
    /**
     * Process the annotations for the filters.
     */
    static void loadApplicationFilterAnnotations(ServletContextConfig context, ClassLoader classLoader) {
        Iterator i1 = context.filters.values().iterator();
        while (i1.hasNext()) {
            FilterData fc = (FilterData) i1.next();
            loadClassAnnotation(context, fc.filterClass, classLoader);
        }
    }
    
    
    /**
     * Process the annotations for the servlets.
     * @param classLoader 
     */
    static void loadApplicationServletAnnotations(ServletContextConfig context, ClassLoader classLoader) {
        Class classClass = null;
        
        
        Iterator i1 = context.servlets.values().iterator();
        while (i1.hasNext()) {
            ServletData sd = (ServletData) i1.next();
            if (sd.servletClass == null) {
                continue;
            }

            try {
                classClass = classLoader.loadClass(sd.servletClass);
            } catch (ClassNotFoundException e) {
                // We do nothing
            } catch (NoClassDefFoundError e) {
                // We do nothing
            }

            if (classClass == null) {
                continue;
            }

            loadClassAnnotation(context, classClass);
            /* Process RunAs annotation which can be only on servlets.
             * Ref JSR 250, equivalent to the run-as element in
             * the deployment descriptor
             */
            if (classClass.isAnnotationPresent(RunAs.class)) {
                RunAs annotation = (RunAs) 
                classClass.getAnnotation(RunAs.class);
                sd.runAs = annotation.value();
            }
        }
    }
    
    /**
     * Process the annotations on a context for a given className.
     */
    static void loadClassAnnotation(ServletContextConfig context, 
                                    String classClass2, ClassLoader classLoader) {
        
        Class classClass = null;
        
        try {
            classClass = classLoader.loadClass(classClass2);
        } catch (ClassNotFoundException e) {
            // We do nothing
        } catch (NoClassDefFoundError e) {
            // We do nothing
        }
        
        if (classClass == null) {
            return;
        }
        loadClassAnnotation(context, classClass);
    }
     
    static void loadClassAnnotation(ServletContextConfig context, 
                                    Class classClass) {
        
        /* Process DeclareRoles annotation.
         * Ref JSR 250, equivalent to the security-role element in
         * the deployment descriptor
         */
        if (classClass.isAnnotationPresent(DeclareRoles.class)) {
            DeclareRoles annotation = (DeclareRoles) 
                classClass.getAnnotation(DeclareRoles.class);
            for (int i = 0; annotation.value() != null && 
                            i < annotation.value().length; i++) {
                context.securityRole.add(annotation.value()[i]);
            }
        }
        
        
    }

    
}
