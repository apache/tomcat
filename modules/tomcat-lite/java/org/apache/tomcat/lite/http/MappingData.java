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

package org.apache.tomcat.lite.http;

import org.apache.tomcat.lite.io.CBuffer;


/**
 * Mapping data.
 *
 * @author Remy Maucherat
 */
public class MappingData {

    public Object context = null; // ServletContextImpl

    public BaseMapper.Context contextMap;

    public BaseMapper.ServiceMapping service = null;

    public CBuffer contextPath = CBuffer.newInstance();
    public CBuffer requestPath = CBuffer.newInstance();
    public CBuffer wrapperPath = CBuffer.newInstance();
    public CBuffer pathInfo = CBuffer.newInstance();

    public CBuffer redirectPath = CBuffer.newInstance();

    // Extension
    CBuffer ext = CBuffer.newInstance();
    CBuffer tmpPrefix = CBuffer.newInstance();

    // Excluding context path, with a '/' added if needed
    CBuffer tmpServletPath = CBuffer.newInstance();

    // Excluding context path, with a '/' added if needed
    CBuffer tmpWelcome = CBuffer.newInstance();

    public void recycle() {
        service = null;
        context = null;
        pathInfo.recycle();
        requestPath.recycle();
        wrapperPath.recycle();
        contextPath.recycle();
        redirectPath.recycle();
        contextMap = null;
    }


    public Object getServiceObject() {
        return service == null ? null : service.object;
    }

}
