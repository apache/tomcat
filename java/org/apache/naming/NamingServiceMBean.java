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

package org.apache.naming;

/**
 * Naming MBean interface.
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @version $Revision$
 */

public interface NamingServiceMBean {
    
    
    // -------------------------------------------------------------- Constants
    
    
    /**
     * Status constants.
     */
    public static final String[] states = 
    {"Stopped", "Stopping", "Starting", "Started"};
    
    
    public static final int STOPPED  = 0;
    public static final int STOPPING = 1;
    public static final int STARTING = 2;
    public static final int STARTED  = 3;
    
    
    /**
     * Component name.
     */
    public static final String NAME = "Apache JNDI Naming Service";
    
    
    /**
     * Object name.
     */
    public static final String OBJECT_NAME = ":service=Naming";
    
    
    // ------------------------------------------------------ Interface Methods
    
    
    /**
     * Retruns the JNDI component name.
     */
    public String getName();
    
    
    /**
     * Returns the state.
     */
    public int getState();
    
    
    /**
     * Returns a String representation of the state.
     */
    public String getStateString();
    
    
    /**
     * Start the servlet container.
     */
    public void start()
        throws Exception;
    
    
    /**
     * Stop the servlet container.
     */
    public void stop();
    
    
    /**
     * Destroy servlet container (if any is running).
     */
    public void destroy();
    
    
}
