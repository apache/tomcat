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

package org.apache.naming.resources;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * Factory for Stream handlers to a JNDI directory context.
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @version $Revision$
 */
public class DirContextURLStreamHandlerFactory 
    implements URLStreamHandlerFactory {
    
    
    // ----------------------------------------------------------- Constructors
    
    
    public DirContextURLStreamHandlerFactory() {
        // NOOP
    }
    
    
    // ----------------------------------------------------- Instance Variables
    
    
    // ------------------------------------------------------------- Properties
    
    
    // ---------------------------------------- URLStreamHandlerFactory Methods
    
    
    /**
     * Creates a new URLStreamHandler instance with the specified protocol.
     * Will return null if the protocol is not <code>jndi</code>.
     * 
     * @param protocol the protocol (must be "jndi" here)
     * @return a URLStreamHandler for the jndi protocol, or null if the 
     * protocol is not JNDI
     */
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (protocol.equals("jndi")) {
            return new DirContextURLStreamHandler();
        } else {
            return null;
        }
    }
    
    
}
