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


package org.apache.catalina.deploy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;


/**
 * Representation of an application resource reference, as represented in
 * an <code>&lt;res-env-refy&gt;</code> element in the deployment descriptor.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 303032 $ $Date: 2004-07-26 18:04:02 +0200 (lun., 26 juil. 2004) $
 */

public class ContextTransaction implements Serializable {


    // ------------------------------------------------------------- Properties


    /**
     * Holder for our configured properties.
     */
    private HashMap properties = new HashMap();

    /**
     * Return a configured property.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Set a configured property.
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /** 
     * remove a configured property.
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    /**
     * List properties.
     */
    public Iterator listProperties() {
        return properties.keySet().iterator();
    }
    
    
    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("Transaction[");
        sb.append("]");
        return (sb.toString());

    }


    // -------------------------------------------------------- Package Methods


    /**
     * The NamingResources with which we are associated (if any).
     */
    protected NamingResources resources = null;

    public NamingResources getNamingResources() {
        return (this.resources);
    }

    void setNamingResources(NamingResources resources) {
        this.resources = resources;
    }


}
