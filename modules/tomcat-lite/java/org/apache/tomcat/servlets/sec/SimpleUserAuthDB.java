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


package org.apache.tomcat.servlets.sec;


import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/** 
 * Load user/passwords from a file.
 * 
 * @author Costin Manolache
 */
public class SimpleUserAuthDB implements UserDB {
    
    private static final String USER_PREFIX = "u.";
    private static final String ROLE_PREFIX = "r.";
    
    HashMap users = new HashMap();
    HashMap roles = new HashMap();
    
    boolean hasMessageDigest = false;
    String realm = null;
    
    public void addUser(String name, String pass) {
        users.put(name, pass);
    }
    
    public void addRole(String user, String value) {
        String[] userRoles = value.split(",");
        roles.put(user, userRoles);
    }
    
    public void setFilename(String fileName) {
        
    }
    
    public void init(Properties p) throws ServletException {
    }
    
    public void init(ServletConfig servletConfig) throws ServletException {
        Enumeration names = servletConfig.getInitParameterNames();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            String value = servletConfig.getInitParameter(name);
            if (name.startsWith(USER_PREFIX)) {
                addUser(name.substring(USER_PREFIX.length()), value);
            }
            if (name.startsWith(ROLE_PREFIX)) {
                addRole(name.substring(ROLE_PREFIX.length()), value);
            }
        }
    }

    public void checkAuth(String method, String cookie) {
        
    }


}
