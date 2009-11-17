/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.servlets.jsp;

import java.util.Stack;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** For SingleThreadedServlet support. 
 * 
 * This is container independent.
 * 
 * Will maintain a pool of servlets, etc 
 * 
 * @author Costin Manolache
 */
public class SingleThreadedProxyServlet extends HttpServlet {

    private Class classClass = null;
    private transient boolean singleThreadModel = false;
    /**
     * Stack containing the STM instances.
     */
    private transient Stack instancePool = null;

    /**
     * Extra params: 
     *   - servlet-class - the class of the single-threaded servlet
     *   - 
     * 
     */
    public void init() {
        
    }
    
    public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException {
        synchronized (instancePool) {
            if (instancePool.isEmpty()) {
                try {
                    Servlet newServlet = null; // loadServlet();

                    // todo: should we init each of them ?
                    
                    newServlet.service(req, res);
                    
                    
                } catch (Throwable e) {
                    throw new ServletException("allocate ",
                            e);
                }
            }
            Servlet s = (Servlet) instancePool.pop();
        }

        
    }
}
