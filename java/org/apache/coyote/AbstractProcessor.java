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
package org.apache.coyote;

import java.util.concurrent.Executor;

import org.apache.tomcat.util.net.AbstractEndpoint;

/**
 * Provides attributes common to all support protocols (currently HTTP and AJP).
 * TODO: There are more attributes that can be pulled up
 */
public abstract class AbstractProcessor implements ActionHook, Processor {

    /**
     * The endpoint receiving connections that are handled by this processor.
     */
    protected AbstractEndpoint endpoint;
    protected AbstractEndpoint getEndpoint() {
        return endpoint;
    }


    /**
     * The request associated with this processor.
     */
    protected Request request = null;
    public Request getRequest() {
        return request;
    }


    /**
     * The response associated with this processor.
     */
    protected Response response = null;


    /*
     * Expose selected endpoint attributes through the processor
     */

    /**
     * Obtain the Executor used by the underlying endpoint.
     */
    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }
}
