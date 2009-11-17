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

package org.apache.tomcat.lite;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.mapper.MappingData;

/** 
 * This handles host and context mapping.
 * 
 * The default implementation for tomcat lite is very limitted - 
 * no support for virtual hosts, no support for contexts deeper than 
 * 1 level. The intention is to override this with more advanced mappers.
 * 
 * With 'ConfigurableHosts' interface it is possible for a smart 
 * mapper to load/unload virtual hosts at runtime, maybe from a 
 * database. It should be possible to use databases to store huge number 
 * of hosts or webapps. 
 * 
 */
public class WebappContextMapper implements Filter {

  ServletContext rootContext;
  Map<MessageBytes, ServletContext> contexts = new HashMap();

  public WebappContextMapper() {
  }

  public void addHost(String name, String[] aliases) {
  }
  
  /**
   * Add a new Context to an existing Host.
   *
   * @param hostName Virtual host name this context belongs to
   * @param contextPath Context path
   * @param context Context object
   * @param welcomeResources Welcome files defined for this context
   * @param resources Static resources of the context
   */
  public void addContext(String hostName, 
                         ServletContext context)
      throws ServletException
  {
    String path = context.getContextPath();
    if (path.lastIndexOf("/") > 0) {
      throw new ServletException("Base context mapper supports only one level");
    }
    if ("/".equals(path)) {
      rootContext = context;
    }
    MessageBytes mb = MessageBytes.newInstance();
    mb.setChars(path.toCharArray(), 0, path.length());
    contexts.put(mb, context);
  }


  /**
   * Remove a context from an existing host.
   *
   * @param hostName Virtual host name this context belongs to
   * @param path Context path
   */
  public void removeContext(String hostName, String path) 
      throws ServletException {
    if ("/".equals(path)) {
      rootContext = null;
    }
    contexts.remove(path);  
  }

  /**
   * Map the specified URI.
   */
  private void mapContext(ServletRequestImpl req)
      throws IOException, ServletException {
    MessageBytes uriMB = req.getDecodedRequestURIMB();
    MappingData mappingData = req.getMappingData(); 
    uriMB.toChars();
    CharChunk uri = uriMB.getCharChunk();

    
    if (uri.length() < 2 || contexts.size() == 0) {
      mappingData.context = rootContext;
      if (rootContext != null) {
        mappingData.contextPath.setString(rootContext.getContextPath());
      }
      return;
    }
    
    int nextSlash = uri.indexOf('/', 1);
    if (nextSlash == -1) {
      nextSlash = uri.length();
    }
    mappingData.contextPath.setChars(uri.getChars(), 0, nextSlash);
    ServletContext servletContext = contexts.get(mappingData.contextPath);

    if (servletContext != null) {
      mappingData.context = servletContext;
    } else {
      mappingData.context = rootContext;
      if (rootContext != null) {
        mappingData.contextPath.setString(rootContext.getContextPath());      
      }
    }
  }

  public void init(FilterConfig filterConfig) throws ServletException {
  }
    
  public void doFilter(ServletRequest request, 
                       ServletResponse response, 
                       FilterChain chain) 
      throws IOException, ServletException {
    ServletRequestImpl req = (ServletRequestImpl)request;
    mapContext(req);
  }
    
  public void destroy() {
  }
}