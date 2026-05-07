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
package org.apache.catalina.mapper;

import javax.servlet.http.MappingMatch;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * Mapping data.
 */
public class MappingData {

    /**
     * Default constructor.
     */
    public MappingData() {
    }

    /**
     * The mapped host.
     */
    public Host host = null;
    /**
     * The mapped context.
     */
    public Context context = null;
    /**
     * The number of slashes in the context path.
     */
    public int contextSlashCount = 0;
    /**
     * The mapped contexts.
     */
    public Context[] contexts = null;
    /**
     * The mapped wrapper.
     */
    public Wrapper wrapper = null;
    /**
     * Whether this is a JSP wildcard mapping.
     */
    public boolean jspWildCard = false;

    /**
     * @deprecated Unused. This will be removed in Tomcat 10.
     */
    @Deprecated
    public final MessageBytes contextPath = MessageBytes.newInstance();
    /**
     * The request path.
     */
    public final MessageBytes requestPath = MessageBytes.newInstance();
    /**
     * The wrapper path.
     */
    public final MessageBytes wrapperPath = MessageBytes.newInstance();
    /**
     * The path info.
     */
    public final MessageBytes pathInfo = MessageBytes.newInstance();

    /**
     * The redirect path.
     */
    public final MessageBytes redirectPath = MessageBytes.newInstance();

    // Fields used by ApplicationMapping to implement javax.servlet.http.HttpServletMapping
    /**
     * The match type.
     */
    public MappingMatch matchType = null;

    /**
     * Recycle this mapping data for reuse.
     */
    public void recycle() {
        host = null;
        context = null;
        contextSlashCount = 0;
        contexts = null;
        wrapper = null;
        jspWildCard = false;
        contextPath.recycle();
        requestPath.recycle();
        wrapperPath.recycle();
        pathInfo.recycle();
        redirectPath.recycle();
        matchType = null;
    }

    @Override
    public final String toString() {
        return "MappingData[" + host + ":" + context + ":" + wrapper + "]";
    }

}
