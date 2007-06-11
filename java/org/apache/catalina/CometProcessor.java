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


package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.Servlet;

/**
 * This interface should be implemented by servlets which would like to handle
 * asynchronous IO, recieving events when data is available for reading, and
 * being able to output data without the need for being invoked by the container.
 * Note: When this interface is implemented, the service method of the servlet will
 * never be called, and will be replaced with a begin event. Should the connector you 
 * have configured not support Comet, the service method will be called, and the 
 * request/response will not be marked as comet, but instead behave like a regular 
 * Servlet<br/>
 * 
 * A Comet request, aka Comet connection, referenced through the #CometEvent and the request/response pair
 * and has a lifecycle somewhat different to a regular servlet.<br/>
 * 
 * Read more about it in the Tomcat documentation about Advanced IO, 
 * 
 * 
 */
public interface CometProcessor extends Servlet 
{

    /**
     * Process the given Comet event.
     * 
     * @param event The Comet event that will be processed
     * @throws IOException
     * @throws ServletException
     * @see CometEvent
     * @see CometEvent#EventType
     */
    public void event(CometEvent event)
        throws IOException, ServletException;

}
