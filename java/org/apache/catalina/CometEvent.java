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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The CometEvent interface.
 * A comet event is the contract between the servlet container and the servlet implementation(CometProcessor) for handling comet connections.
 * 
 * @see CometProcessor
 * @author Filip Hanik
 * @author Remy Maucherat
 */
public interface CometEvent {

    /**
     * Enumeration describing the major events that the container can invoke 
     * the CometProcessors event() method with
     * BEGIN - will be called at the beginning 
     *  of the processing of the connection. It can be used to initialize any relevant 
     *  fields using the request and response objects. Between the end of the processing 
     *  of this event, and the beginning of the processing of the end or error events,
     *  it is possible to use the response object to write data on the open connection.
     *  Note that the response object and depedent OutputStream and Writer are still 
     *  not synchronized, so when they are accessed by multiple threads, 
     *  synchronization is mandatory. After processing the initial event, the request 
     *  is considered to be committed.
     * READ - This indicates that input data is available, and that one read can be made
     *  without blocking. The available and ready methods of the InputStream or
     *  Reader may be used to determine if there is a risk of blocking: the servlet
     *  should read while data is reported available. When encountering a read error, 
     *  the servlet should report it by propagating the exception properly. Throwing 
     *  an exception will cause the error event to be invoked, and the connection 
     *  will be closed. 
     *  Alternately, it is also possible to catch any exception, perform clean up
     *  on any data structure the servlet may be using, and using the close method
     *  of the event. It is not allowed to attempt reading data from the request 
     *  object outside of the execution of this method.
     * END - End may be called to end the processing of the request. Fields that have
     *  been initialized in the begin method should be reset. After this event has
     *  been processed, the request and response objects, as well as all their dependent
     *  objects will be recycled and used to process other requests. End will also be 
     *  called when data is available and the end of file is reached on the request input
     *  (this usually indicates the client has pipelined a request).
     * ERROR - Error will be called by the container in the case where an IO exception
     *  or a similar unrecoverable error occurs on the connection. Fields that have
     *  been initialized in the begin method should be reset. After this event has
     *  been processed, the request and response objects, as well as all their dependent
     *  objects will be recycled and used to process other requests.
     * CALLBACK - Callback will be called by the container after the comet processor
     *  has registered for the OP_CALLBACK operation.
     *  This allows you get an event instantly, and you can perform IO actions
     *  or close the Comet connection.
     * WRITE - Write is called, only if the Comet processor has registered for the OP_WRITE
     *  event. This means that connection is ready to receive data to be written out.
     */
    public enum EventType {BEGIN, READ, END, ERROR, WRITE, CALLBACK}
    
    
    /**
     * Event details
     * TIMEOUT - the connection timed out (sub type of ERROR); note that this ERROR type is not fatal, and
     *   the connection will not be closed unless the servlet uses the close method of the event
     * CLIENT_DISCONNECT - the client connection was closed (sub type of ERROR)
     * IOEXCEPTION - an IO exception occurred, such as invalid content, for example, an invalid chunk block (sub type of ERROR)
     * WEBAPP_RELOAD - the webapplication is being reloaded (sub type of END)
     * SERVER_SHUTDOWN - the server is shutting down (sub type of END)
     * SESSION_END - the servlet ended the session (sub type of END)

     */
    public enum EventSubType { TIMEOUT, CLIENT_DISCONNECT, IOEXCEPTION, WEBAPP_RELOAD, SERVER_SHUTDOWN, SESSION_END }
    
    
    /**
     * Returns the HttpServletRequest.
     * 
     * @return HttpServletRequest
     */
    public HttpServletRequest getHttpServletRequest();
    
    /**
     * Returns the HttpServletResponse.
     * 
     * @return HttpServletResponse
     */
    public HttpServletResponse getHttpServletResponse();
    
    /**
     * Returns the event type.
     * 
     * @return EventType
     * @see #EventType
     */
    public EventType getEventType();
    
    /**
     * Returns the sub type of this event.
     * 
     * @return EventSubType
     * @see #EventSubType
     */
    public EventSubType getEventSubType();
    
    /**
     * Ends the Comet session. This signals to the container that 
     * the container wants to end the comet session. This will send back to the
     * client a notice that the server has no more data to send as part of this
     * request. The servlet should perform any needed cleanup as if it had recieved
     * an END or ERROR event. 
     * 
     * @throws IOException if an IO exception occurs
     */
    public void close() throws IOException;
    
    /**
     * Sets the timeout for this Comet connection. Please NOTE, that the implementation 
     * of a per connection timeout is OPTIONAL and MAY NOT be implemented.<br/>
     * This method sets the timeout in milliseconds of idle time on the connection.
     * The timeout is reset every time data is received from the connection or data is flushed
     * using <code>response.flushBuffer()</code>. If a timeout occurs, the 
     * <code>error(HttpServletRequest, HttpServletResponse)</code> method is invoked. The 
     * web application SHOULD NOT attempt to reuse the request and response objects after a timeout
     * as the <code>error(HttpServletRequest, HttpServletResponse)</code> method indicates.<br/>
     * This method should not be called asynchronously, as that will have no effect.
     * 
     * @param timeout The timeout in milliseconds for this connection, must be a positive value, larger than 0
     * @throws IOException An IOException may be thrown to indicate an IO error, 
     *         or that the EOF has been reached on the connection
     * @throws ServletException An exception has occurred, as specified by the root
     *         cause
     * @throws UnsupportedOperationException if per connection timeout is not supported, either at all or at this phase
     *         of the invocation.
     */
    public void setTimeout(int timeout)
        throws IOException, ServletException, UnsupportedOperationException;
    
    

    /**
     * COMET_NON_BLOCKING<br/>
     * Option bit set for allowing non blocking IO
     * when reading from the request or writing to the response<br/>
     * COMET_BLOCKING<br/>
     * Configure the comet connection for blocking IO, this is the default setting
     * 
     * @see #configure(int)
     */
    public enum CometConfiguration {COMET_BLOCKING, COMET_NON_BLOCKING};
        
    /**
     * Configures the connection for desired IO options.
     * By default a Comet connection is configured for <br/>
     * a) Blocking IO - standard servlet usage<br/>
     * b) Register for READ events when data arrives<br/>
     * Tomcat Comet allows you to configure for additional options:<br/>
     * the <code>COMET_NON_BLOCKING</code> bit signals whether writing and reading from the request 
     * or writing to the response will be non blocking.<br/>
     * the <code>COMET_BLOCKING</code> bit signals the container you wish for read and write to be done in a blocking fashion
     * @param cometOptions int - the option bit set
     * @throws IllegalStateException - if this method is invoked outside of the BEGIN event
     * @see #CometConfiguration
     * @see #isReadable()
     * @see #isWriteable()
     */
    public void configure(CometConfiguration... options) throws IllegalStateException;
    
    /**
     * Returns the configuration for this Comet connection
     * @return CometConfiguration[]
     * @see #configure(CometConfiguration...)
     */
    public CometConfiguration[] getConfiguration();
    
    /**
     * OP_CALLBACK - receive a CALLBACK event from the container
     * OP_READ - receive a READ event when the connection has data to be read
     * OP_WRITE - receive a WRITE event when the connection is able to receive data to be written
     * @see #register(CometOperations)
     */
    public enum CometOperation {OP_CALLBACK, OP_READ, OP_WRITE};
    
    /**
     * Registers the Comet connection with the container for IO notifications.
     * These could be notifications 
     * @param operations
     * @throws IllegalStateException - if you are trying to register with a socket that already is registered
     * or if the operation you are trying to register is invalid.
     */
    public void register(CometOperation... operations) throws IllegalStateException;
    
    /**
     * Unregisters Comet operations for this CometConnection
     * @param operations CometOperation[]
     * @throws IllegalStateException
     */
    public void unregister(CometOperation... operations) throws IllegalStateException;

    /**
     * Returns what the current IO notifications that the Comet
     * connection is registered for.
     * @return CometOperations[]
     * @see #register(CometOperations...)
     */
    public CometOperation[] getRegisteredOps();
    
    /**
     * Returns true if the Comet connection is blocking or non blocking and you can write
     * without blocking data to the response
     * @return boolean - true if you can write to the response 
     */
    public boolean isWriteable();
    
    /**
     * Returns true if the Comet connection is blocking or non blocking and data is available to be read
     * @see javax.servlet.ServletRequest#getInputStream()#available()>0
     * @return boolean
     */
    public boolean isReadable();

}
