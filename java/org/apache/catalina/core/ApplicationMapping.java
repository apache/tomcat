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
package org.apache.catalina.core;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.mapper.MappingData;

public class ApplicationMapping {

    private final MappingData mappingData;

    private volatile ApplicationMappingImpl mapping = null;

    public ApplicationMapping(MappingData mappingData) {
        this.mappingData = mappingData;
    }

    public ApplicationMappingImpl getHttpServletMapping() {
        if (mapping == null) {
            if (mappingData == null) {
                // This can happen when dispatching from an application provided
                // request object that does not provide the Servlet 4.0 mapping
                // data.
                mapping = new ApplicationMappingImpl("", "", null, "");
            } else {
                String servletName;
                if (mappingData.wrapper == null) {
                    servletName = "";
                } else {
                    servletName = mappingData.wrapper.getName();
                }
                if (mappingData.matchType == null) {
                    mapping = new ApplicationMappingImpl("", "", null, servletName);
                } else {
                    switch (mappingData.matchType) {
                        case CONTEXT_ROOT:
                            mapping = new ApplicationMappingImpl("", "", mappingData.matchType, servletName);
                            break;
                        case DEFAULT:
                            mapping = new ApplicationMappingImpl("", "/", mappingData.matchType, servletName);
                            break;
                        case EXACT:
                            mapping = new ApplicationMappingImpl(mappingData.wrapperPath.toString().substring(1),
                                    mappingData.wrapperPath.toString(), mappingData.matchType, servletName);
                            break;
                        case EXTENSION:
                            String path = mappingData.wrapperPath.toString();
                            int extIndex = path.lastIndexOf('.');
                            mapping = new ApplicationMappingImpl(path.substring(1, extIndex),
                                    "*" + path.substring(extIndex), mappingData.matchType, servletName);
                            break;
                        case PATH:
                            String matchValue;
                            if (mappingData.pathInfo.isNull()) {
                                matchValue = null;
                            } else {
                                matchValue = mappingData.pathInfo.toString().substring(1);
                            }
                            mapping = new ApplicationMappingImpl(matchValue, mappingData.wrapperPath.toString() + "/*",
                                    mappingData.matchType, servletName);
                            break;
                    }
                }
            }
        }

        return mapping;
    }

    public void recycle() {
        mapping = null;
    }


    public static ApplicationMappingImpl getHttpServletMapping(HttpServletRequest request) {
        if (request instanceof RequestFacade) {
            return ((RequestFacade) request).getHttpServletMapping();
        } else if (request instanceof Request) {
            return ((Request) request).getHttpServletMapping();
        } else if (request instanceof ApplicationHttpRequest) {
            return ((ApplicationHttpRequest) request).getHttpServletMapping();
        }
        return (new ApplicationMapping(null)).getHttpServletMapping();
    }


    public static ApplicationMappingImpl getHttpServletMapping(HttpServletRequestWrapper wrapper) {
        if (wrapper instanceof ApplicationHttpRequest) {
            return ((ApplicationHttpRequest) wrapper).getHttpServletMapping();
        }
        return (new ApplicationMapping(null)).getHttpServletMapping();
    }
}
