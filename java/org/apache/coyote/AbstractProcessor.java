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

import java.io.IOException;
import java.util.concurrent.Executor;

import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Provides functionality and attributes common to all supported protocols
 * (currently HTTP and AJP).
 */
public abstract class AbstractProcessor<S> implements ActionHook, Processor {

    protected Adapter adapter;
    protected final AsyncStateMachine asyncStateMachine;
    protected final AbstractEndpoint endpoint;
    protected final Request request;
    protected final Response response;


    public AbstractProcessor(AbstractEndpoint endpoint) {
        this.endpoint = endpoint;
        asyncStateMachine = new AsyncStateMachine(this);

        request = new Request();

        response = new Response();
        response.setHook(this);
        request.setResponse(response);

    }


    /**
     * The endpoint receiving connections that are handled by this processor.
     */
    protected AbstractEndpoint getEndpoint() {
        return endpoint;
    }


    /**
     * The request associated with this processor.
     */
    public Request getRequest() {
        return request;
    }


    /**
     * Set the associated adapter.
     *
     * @param adapter the new adapter
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    /**
     * Get the associated adapter.
     *
     * @return the associated adapter
     */
    public Adapter getAdapter() {
        return adapter;
    }


    /**
     * Obtain the Executor used by the underlying endpoint.
     */
    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }


    public boolean isAsync() {
        return asyncStateMachine.isAsync();
    }


    public SocketState asyncPostProcess() {
        return asyncStateMachine.asyncPostProcess();
    }

    protected abstract boolean isComet();

    /**
     * Process HTTP requests. All requests are treated as HTTP requests to start
     * with although they may change type during processing.
     */
    public abstract SocketState process(SocketWrapper<S> socket)
        throws IOException;


    /**
     * Process in-progress Comet requests. These will start as HTTP requests.
     */
    public abstract SocketState event(SocketStatus status) throws IOException;

    /**
     * Process in-progress Servlet 3.0 Async requests. These will start as HTTP
     * requests.
     */
    public abstract SocketState asyncDispatch(SocketStatus status);
}
