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

package org.apache.tomcat.lite.webxml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletException;

import org.apache.tomcat.lite.webxml.ServletContextConfig.EnvEntryData;
import org.apache.tomcat.lite.webxml.ServletContextConfig.FilterData;
import org.apache.tomcat.lite.webxml.ServletContextConfig.FilterMappingData;
import org.apache.tomcat.lite.webxml.ServletContextConfig.SecurityConstraintData;
import org.apache.tomcat.lite.webxml.ServletContextConfig.ServletData;
import org.apache.tomcat.lite.webxml.ServletContextConfig.WebResourceCollectionData;
import org.apache.tomcat.util.DomUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** 
 * General-purpose utility to process an web.xml file. Result
 *  is a tree of objects starting with WebAppData.
 * 
 * TODO: allow writting of web.xml, allow modification ( preserving 
 * comments )
 * 
 * @author costin
 */
public class WebXml {
    ServletContextConfig d;
    
    public WebXml(ServletContextConfig cfg) {
        d = cfg;
    }

    public ServletContextConfig getWebAppData() {
        return d;
    }
    
    public void readWebXml(String baseDir) throws ServletException {
        try {
            File webXmlFile = new File( baseDir + "/WEB-INF/web.xml");
            if (!webXmlFile.exists()) {
                return;
            }
            d.fileName = webXmlFile.getCanonicalPath();
            d.timestamp = webXmlFile.lastModified();
            
            FileInputStream fileInputStream = new FileInputStream(webXmlFile);
            readWebXml(fileInputStream);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServletException(e);
        }
    }
    
     public void readWebXml(InputStream fileInputStream) 
             throws ServletException {
         try {
                    
            Document document = DomUtil.readXml(fileInputStream);
            Node webappNode = DomUtil.getChild(document, "web-app");

            String fullS = DomUtil.getAttribute(webappNode, "full");
            if (fullS != null && fullS.equalsIgnoreCase("true")) {
                d.full = true;
            }
            
            d.displayName = DomUtil.getAttribute(webappNode, "display-name");
                        
            // Process each child of web-app
            Node confNode = DomUtil.getChild(webappNode, "filter");
            while (confNode != null ) {
                processFilter(confNode);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "filter-mapping");
            while (confNode != null ) {
                processFilterMapping(confNode);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "context-param");
            while (confNode != null ) {
                String n = DomUtil.getChildContent(confNode, "param-name").trim();
                String v = DomUtil.getChildContent(confNode, "param-value").trim();
                d.contextParam.put(n, v);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "mime-mapping");
            while (confNode != null ) {
                String n = DomUtil.getChildContent(confNode, "extension");
                String t = DomUtil.getChildContent(confNode, "mime-type");
                d.mimeMapping.put(n, t);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "error-page");
            while (confNode != null ) {
                processErrorPage(confNode);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "jsp-config");
            while (confNode != null ) {
                processJspConfig(confNode);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "servlet");
            while (confNode != null ) {
                processServlet(confNode);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "servlet-mapping");
            while (confNode != null ) {
                processServletMapping(confNode);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "listener");
            while (confNode != null ) {
                String lClass = DomUtil.getChildContent(confNode, "listener-class");
                d.listenerClass.add(lClass);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "security-constraint");
            while (confNode != null ) {
                processSecurityConstraint(confNode);
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(webappNode, "login-config");
            while (confNode != null ) {
                processLoginConfig(confNode);
                confNode = DomUtil.getNext(confNode);
                if (confNode != null) 
                    throw new ServletException("Multiple login-config");
            }

            confNode = DomUtil.getChild(webappNode, "session-config");
            while (confNode != null ) {
                String n = DomUtil.getChildContent(confNode, "session-timeout");
                int stout = Integer.parseInt(n);
                d.sessionTimeout = stout;
                confNode = DomUtil.getNext(confNode);
                if (confNode != null) 
                    throw new ServletException("Multiple session-config");
            }

            confNode = DomUtil.getChild(webappNode, "welcome-file-list");
            while (confNode != null ) {
                Node wf = DomUtil.getChild(confNode, "welcome-file");
                while (wf != null) {
                    String file = DomUtil.getContent(wf);
                    d.welcomeFileList.add(file);
                    wf = DomUtil.getNext(wf);
                }
                // more sections ?
                confNode = DomUtil.getNext(confNode);
            }

            // Not supported right now - TODO: collect, have jndi plugin
            confNode = DomUtil.getChild(webappNode, "env-entry");
            while (confNode != null ) {
                processEnvEntry(confNode);
                confNode = DomUtil.getNext(confNode);
            }
            
            confNode = DomUtil.getChild(webappNode, "locale-encoding-mapping-list");
            while (confNode != null ) {
                confNode = DomUtil.getNext(confNode);
                String n = DomUtil.getChildContent(confNode, "locale");
                String t = DomUtil.getChildContent(confNode, "encoding");
                d.localeEncodingMapping.put(n, t);
            }

            confNode = DomUtil.getChild(webappNode, "distributable");
            while (confNode != null ) {
                d.distributable = true;
                confNode = DomUtil.getNext(confNode);
            }

            confNode = DomUtil.getChild(confNode, "security-role");
            while (confNode != null ) {
                String n = DomUtil.getChildContent(confNode, "role-name");
                d.securityRole.add(n);
                confNode = DomUtil.getNext(confNode);
            }

                } catch (Exception e) {
                    e.printStackTrace();
                    throw new ServletException(e);
                }
    }
    
    private void processJspConfig(Node confNode) {
        Node tagLib = DomUtil.getChild(confNode, "taglib");
        while (tagLib != null) {
            String uri = DomUtil.getChildContent(tagLib, "taglib-uri");
            String l = DomUtil.getChildContent(tagLib, "taglib-location");
            //d.tagLibs.put(uri, l);
            tagLib = DomUtil.getNext(tagLib);
        }
        
        tagLib = DomUtil.getChild(confNode, "jsp-property-group");
        while (tagLib != null) {
            // That would be the job of the JSP servlet to process.
            tagLib = DomUtil.getNext(tagLib);
        }
    }

    private void processEnvEntry(Node confNode) {
        EnvEntryData ed = new EnvEntryData();
        ed.envEntryName = DomUtil.getChildContent(confNode,"env-entry-name");
        ed.envEntryType = DomUtil.getChildContent(confNode,"env-entry-type");
        ed.envEntryValue = DomUtil.getChildContent(confNode,"env-entry-value");
        d.envEntry.add(ed);
    }

    private void processLoginConfig(Node confNode) {
        d.authMethod = DomUtil.getChildContent(confNode,"auth-method");
        d.realmName = DomUtil.getChildContent(confNode,"auth-method");
        Node formNode = DomUtil.getChild(confNode, "form-login-config");
        if (formNode != null) {
            d.formLoginPage = DomUtil.getChildContent(formNode,"form-login-page");
            d.formErrorPage = DomUtil.getChildContent(formNode,"form-error-page");
        }
    }

    private void processSecurityConstraint(Node confNode) {
        SecurityConstraintData sd = new SecurityConstraintData();
        Node cn = DomUtil.getChild(confNode, "web-resource-collection");
        while (cn != null) {
            WebResourceCollectionData wrd = new WebResourceCollectionData();
            wrd.webResourceName = DomUtil.getChildContent(cn, "web-resource-name");
            Node scn = DomUtil.getChild(cn,"url-pattern");
            while (scn != null) {
                wrd.urlPattern.add(DomUtil.getContent(scn));
                scn = DomUtil.getNext(scn);
            }
            scn = DomUtil.getChild(cn,"http-method");
            while (scn != null) {
                wrd.httpMethod.add(DomUtil.getContent(scn));
                scn = DomUtil.getNext(scn);
            }
            cn = DomUtil.getNext(cn);
            sd.webResourceCollection.add(wrd);
        }
        
        d.securityConstraint.add(sd);
    }

    private void processErrorPage(Node confNode) {
        String name = DomUtil.getChildContent(confNode,"location");
        String c = DomUtil.getChildContent(confNode,"error-code");
        String t = DomUtil.getChildContent(confNode,"exception-type");
        if (c != null) {
            d.errorPageCode.put(c, name);
        }
        if (t != null) {
            d.errorPageException.put(t, name);
        }
    }

    private void processServlet(Node confNode) throws ServletException {
        ServletData sd = new ServletData();

        sd.servletName = DomUtil.getChildContent(confNode,"servlet-name");
        sd.servletClass = DomUtil.getChildContent(confNode,"servlet-class");
        sd.jspFile = DomUtil.getChildContent(confNode,"jsp-file");
        
        processInitParams(confNode, sd.initParams);
        
        d.servlets.put( sd.servletName, sd );
        
        String los = DomUtil.getChildContent(confNode, "load-on-startup");
        if (los != null ) { 
            sd.loadOnStartup = Integer.parseInt(los);
        }
        
        Node sn = DomUtil.getChild(confNode, "security-role-ref");
        while (sn != null ) {
            String roleName = DomUtil.getChildContent(sn, "role-name");
            String roleLink = DomUtil.getChildContent(sn, "role-link");
            if (roleLink == null) {
                sd.securityRoleRef.put(roleName, "");
            } else {
                sd.securityRoleRef.put(roleName, roleLink);
            }
            sn = DomUtil.getNext(sn);
        }
    }

    private void processInitParams(Node confNode, HashMap initParams) {
        Node initN = DomUtil.getChild(confNode, "init-param");
        while (initN != null ) {
            String n = DomUtil.getChildContent(initN, "param-name");
            String v = DomUtil.getChildContent(initN, "param-value");
            initParams.put(n, v);
            initN = DomUtil.getNext(initN);
        }
    }

    private void processServletMapping(Node confNode) {
        String name = DomUtil.getChildContent(confNode,"servlet-name");
        Node dataN = DomUtil.getChild(confNode, "url-pattern");
        while (dataN != null) {
            String path = DomUtil.getContent(dataN).trim();
            dataN = DomUtil.getNext(dataN);
            
            if (! (path.startsWith("/") || path.startsWith("*"))) {
                // backward compat 
                path = "/" + path;
            }
            d.servletMapping.put(path, name);
        }
    }

    private void processFilterMapping(Node confNode) {
      String filterName = DomUtil.getChildContent(confNode,"filter-name");
      // multiple 
      ArrayList dispatchers = new ArrayList();
      Node dataN = DomUtil.getChild(confNode, "dispatcher");
      while (dataN != null ) {
          String d = DomUtil.getContent(dataN);
          dispatchers.add(d);
          dataN = DomUtil.getNext(dataN);
      }
      
      // Multiple url-pattern and servlet-name in one
      // mapping rule. Need to be applied in order.
      dataN = DomUtil.getChild(confNode, "url-pattern");
      while (dataN != null ) {
        FilterMappingData fm = new FilterMappingData();
        fm.filterName = filterName;
        fm.dispatcher = dispatchers;
        String path = DomUtil.getContent(dataN);
        dataN = DomUtil.getNext(dataN);
        fm.urlPattern = path;
        d.filterMappings.add(fm);
      }
      dataN = DomUtil.getChild(confNode, "servlet-name");
      while (dataN != null ) {
        FilterMappingData fm = new FilterMappingData();
        fm.filterName = filterName;
        fm.dispatcher = dispatchers;
        String sn = DomUtil.getContent(dataN);
        dataN = DomUtil.getNext(dataN);
        fm.servletName = sn;
        d.filterMappings.add(fm);
      }
    }

    private void processFilter(Node confNode) {
        String name = DomUtil.getChildContent(confNode,"filter-name");
        String sclass = DomUtil.getChildContent(confNode,"filter-class");
        
        FilterData fd = new FilterData();
        processInitParams(confNode, fd.initParams);
        fd.filterName = name;
        fd.filterClass = sclass;
        d.filters.put(name, fd);
    }
    
}
