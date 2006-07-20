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
package org.apache.jasper.compiler;


import java.util.Vector;
import java.util.Hashtable;

import org.apache.jasper.JasperException;

/**
 * Repository of {page, request, session, application}-scoped beans 
 *
 * @author Mandar Raje
 */
class BeanRepository {

    private Vector sessionBeans;
    private Vector pageBeans;
    private Vector appBeans;
    private Vector requestBeans;
    private Hashtable beanTypes;
    private ClassLoader loader;
    private ErrorDispatcher errDispatcher;

    /*
     * Constructor.
     */    
    public BeanRepository(ClassLoader loader, ErrorDispatcher err) {

        this.loader = loader;
	this.errDispatcher = err;

	sessionBeans = new Vector(11);
	pageBeans = new Vector(11);
	appBeans = new Vector(11);
	requestBeans = new Vector(11);
	beanTypes = new Hashtable();
    }
        
    public void addBean(Node.UseBean n, String s, String type, String scope)
	    throws JasperException {

	if (scope == null || scope.equals("page")) {
	    pageBeans.addElement(s);	
	} else if (scope.equals("request")) {
	    requestBeans.addElement(s);
	} else if (scope.equals("session")) {
	    sessionBeans.addElement(s);
	} else if (scope.equals("application")) {
	    appBeans.addElement(s);
	} else {
	    errDispatcher.jspError(n, "jsp.error.usebean.badScope");
	}
	
	putBeanType(s, type);
    }
            
    public Class getBeanType(String bean) throws JasperException {
	Class clazz = null;
	try {
	    clazz = loader.loadClass ((String)beanTypes.get(bean));
	} catch (ClassNotFoundException ex) {
	    throw new JasperException (ex);
	}
	return clazz;
    }
      
    public boolean checkVariable (String bean) {
	// XXX Not sure if this is the correct way.
	// After pageContext is finalised this will change.
	return (checkPageBean(bean) || checkSessionBean(bean) ||
		checkRequestBean(bean) || checkApplicationBean(bean));
    }


    private void putBeanType(String bean, String type) {
	beanTypes.put (bean, type);
    }

    private boolean checkPageBean (String s) {
	return pageBeans.contains (s);
    }

    private boolean checkRequestBean (String s) {
	return requestBeans.contains (s);
    }

    private boolean checkSessionBean (String s) {
	return sessionBeans.contains (s);
    }

    private boolean checkApplicationBean (String s) {
	return appBeans.contains (s);
    }

}




