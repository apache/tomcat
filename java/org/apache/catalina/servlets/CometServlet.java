/*
 * Copyright 2006 The Apache Software Foundation.
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


package org.apache.catalina.servlets;


import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.CometProcessor;


/**
 * Helper class to implement Comet functionality.
 */
public abstract class CometServlet
    extends HttpServlet implements CometProcessor {

    public void begin(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        request.setAttribute("org.apache.tomcat.comet", Boolean.TRUE);
    }
    
    public void end(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        request.removeAttribute("org.apache.tomcat.comet");
    }
    
    public void error(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        end(request, response);
    }
    
    public boolean read(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        InputStream is = request.getInputStream();
        byte[] buf = new byte[512];
        do {
            int n = is.read(buf);
            if (n > 0) {
                // Do something with the data
            } else if (n < 0) {
                return false;
            }
        } while (is.available() > 0);
        return true;
    }

    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        
        if (request.getAttribute("org.apache.tomcat.comet.support") == Boolean.TRUE) {
            begin(request, response);
        } else {
            // No Comet support: regular servlet handling
            begin(request, response);
            boolean error = true;
            try {
                // Loop reading data
                while (read(request, response));
                error = false;
            } finally {
                if (error) {
                    error(request, response);
                } else {
                    end(request, response);
                }
            }
        }
    }
    
    public void setTimeout(HttpServletRequest request, HttpServletResponse response, int timeout)
        throws IOException, ServletException, UnsupportedOperationException {
        if (request.getAttribute("org.apache.tomcat.comet.timeout.support") == Boolean.TRUE) {
            request.setAttribute("org.apache.tomcat.comet.timeout",new Integer(timeout));
        } else {
            throw new UnsupportedOperationException();
        }
    }


}
