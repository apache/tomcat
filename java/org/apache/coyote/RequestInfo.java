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

import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;


/**
 * Structure holding the Request and Response objects. It also holds statistical information about request processing
 * and provide management information about the requests being processed. Each thread uses a Request/Response pair that
 * is recycled on each request. This object provides a place to collect global low-level statistics - without having to
 * deal with synchronization (since each thread will have its own RequestProcessorMX).
  */
public class RequestInfo {
    /**
     * Global request group info.
     */
    private RequestGroupInfo global = null;

    // ----------------------------------------------------------- Constructors

    /**
     * Create a new instance.
     *
     * @param req the request
     */
    public RequestInfo(Request req) {
        this.req = req;
    }

    /**
     * Return the global request processor.
     *
     * @return the global processor
     */
    public RequestGroupInfo getGlobalProcessor() {
        return global;
    }

    /**
     * Set the global request processor.
     *
     * @param global the global processor
     */
    public void setGlobalProcessor(RequestGroupInfo global) {
        if (global != null) {
            this.global = global;
            global.addRequestProcessor(this);
        } else {
            if (this.global != null) {
                this.global.removeRequestProcessor(this);
                this.global = null;
            }
        }
    }


    // ----------------------------------------------------- Instance Variables
    private final Request req;
    private int stage = Constants.STAGE_NEW;
    private String workerThreadName;
    private ObjectName rpName;

    // -------------------- Information about the current request -----------
    // This is useful for long-running requests only

    /**
     * Return the HTTP method.
     *
     * @return the HTTP method
     */
    public String getMethod() {
        return req.getMethod();
    }

    /**
     * Return the current request URI.
     *
     * @return the current URI
     */
    public String getCurrentUri() {
        return req.requestURI().toString();
    }

    /**
     * Return the current query string.
     *
     * @return the current query string
     */
    public String getCurrentQueryString() {
        return req.queryString().toString();
    }

    /**
     * Return the protocol.
     *
     * @return the protocol
     */
    public String getProtocol() {
        return req.protocol().toString();
    }

    /**
     * Return the virtual host.
     *
     * @return the virtual host
     */
    public String getVirtualHost() {
        return req.serverName().toString();
    }

    /**
     * Return the server port.
     *
     * @return the server port
     */
    public int getServerPort() {
        return req.getServerPort();
    }

    /**
     * Return the remote address.
     *
     * @return the remote address
     */
    public String getRemoteAddr() {
        req.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, null);
        return req.remoteAddr().toString();
    }

    /**
     * Return the peer address.
     *
     * @return the peer address
     */
    public String getPeerAddr() {
        req.action(ActionCode.REQ_PEER_ADDR_ATTRIBUTE, null);
        return req.peerAddr().toString();
    }

    /**
     * Obtain the remote address for this connection as reported by an intermediate proxy (if any).
     *
     * @return The remote address for this connection
     */
    public String getRemoteAddrForwarded() {
        String remoteAddrProxy = (String) req.getAttribute(Constants.REMOTE_ADDR_ATTRIBUTE);
        if (remoteAddrProxy == null) {
            return getRemoteAddr();
        }
        return remoteAddrProxy;
    }

    /**
     * Return the content length.
     *
     * @return the content length
     */
    public int getContentLength() {
        return req.getContentLength();
    }

    /**
     * Return the bytes received for the current request.
     *
     * @return the bytes received
     */
    public long getRequestBytesReceived() {
        return req.getBytesRead();
    }

    /**
     * Return the bytes sent for the current request.
     *
     * @return the bytes sent
     */
    public long getRequestBytesSent() {
        return req.getResponse().getContentWritten();
    }

    /**
     * Return the processing time for the current request.
     *
     * @return the processing time
     */
    public long getRequestProcessingTime() {
        // Not perfect, but good enough to avoid returning strange values due to
        // concurrent updates.
        long startTime = req.getStartTimeNanos();
        if (getStage() == Constants.STAGE_ENDED || startTime < 0) {
            return 0;
        } else {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        }
    }

    // -------------------- Statistical data --------------------
    // Collected at the end of each request.
    private long bytesSent;
    private long bytesReceived;

    // Total time = divide by requestCount to get average.
    private long processingTime;
    // The longest response time for a request
    private long maxTime;
    // URI of the request that took maxTime
    private String maxRequestUri;

    private int requestCount;
    // number of response codes >= 400
    private int errorCount;

    // the time of the last request
    private long lastRequestProcessingTime = 0;


    /**
     * Called by the processor before recycling the request. It'll collect statistic information.
     */
    void updateCounters() {
        bytesReceived += req.getBytesRead();
        bytesSent += req.getResponse().getContentWritten();

        requestCount++;
        if (req.getResponse().getStatus() >= 400) {
            errorCount++;
        }
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - req.getStartTimeNanos());
        this.lastRequestProcessingTime = time;
        processingTime += time;
        if (maxTime < time) {
            maxTime = time;
            maxRequestUri = req.requestURI().toString();
        }
    }

    /**
     * Return the current processing stage.
     *
     * @return the stage
     */
    public int getStage() {
        return stage;
    }

    /**
     * Set the current processing stage.
     *
     * @param stage the stage
     */
    public void setStage(int stage) {
        this.stage = stage;
    }

    /**
     * Return the total bytes sent.
     *
     * @return the bytes sent
     */
    public long getBytesSent() {
        return bytesSent;
    }

    /**
     * Reset the bytes sent.
     *
     * @param bytesSent the new bytes sent
     */
    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }

    /**
     * Return the total bytes received.
     *
     * @return the bytes received
     */
    public long getBytesReceived() {
        return bytesReceived;
    }

    /**
     * Reset the bytes received.
     *
     * @param bytesReceived the new bytes received
     */
    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    /**
     * Return the total processing time.
     *
     * @return the processing time
     */
    public long getProcessingTime() {
        return processingTime;
    }

    /**
     * Reset the processing time.
     *
     * @param processingTime the new processing time
     */
    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    /**
     * Return the maximum request processing time.
     *
     * @return the maximum time
     */
    public long getMaxTime() {
        return maxTime;
    }

    /**
     * Reset the maximum time.
     *
     * @param maxTime the new maximum time
     */
    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
    }

    /**
     * Return the URI of the request with the longest processing time.
     *
     * @return the maximum request URI
     */
    public String getMaxRequestUri() {
        return maxRequestUri;
    }

    /**
     * Set the maximum request URI.
     *
     * @param maxRequestUri the maximum request URI
     */
    public void setMaxRequestUri(String maxRequestUri) {
        this.maxRequestUri = maxRequestUri;
    }

    /**
     * Return the request count.
     *
     * @return the request count
     */
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Reset the request count.
     *
     * @param requestCount the new request count
     */
    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    /**
     * Return the error count.
     *
     * @return the error count
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Reset the error count.
     *
     * @param errorCount the new error count
     */
    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    /**
     * Return the worker thread name.
     *
     * @return the worker thread name
     */
    public String getWorkerThreadName() {
        return workerThreadName;
    }

    /**
     * Return the MBean name.
     *
     * @return the MBean name
     */
    public ObjectName getRpName() {
        return rpName;
    }

    /**
     * Return the processing time of the last request.
     *
     * @return the last request processing time
     */
    public long getLastRequestProcessingTime() {
        return lastRequestProcessingTime;
    }

    /**
     * Set the worker thread name.
     *
     * @param workerThreadName the worker thread name
     */
    public void setWorkerThreadName(String workerThreadName) {
        this.workerThreadName = workerThreadName;
    }

    /**
     * Set the MBean name.
     *
     * @param rpName the MBean name
     */
    public void setRpName(ObjectName rpName) {
        this.rpName = rpName;
    }

    /**
     * Set the last request processing time.
     *
     * @param lastRequestProcessingTime the last request processing time
     */
    public void setLastRequestProcessingTime(long lastRequestProcessingTime) {
        this.lastRequestProcessingTime = lastRequestProcessingTime;
    }

    /**
     * Recycle all statistics.
     */
    public void recycleStatistcs() {
        this.bytesSent = 0;
        this.bytesReceived = 0;

        this.processingTime = 0;
        this.maxTime = 0;
        this.maxRequestUri = null;

        this.requestCount = 0;
        this.errorCount = 0;

        this.lastRequestProcessingTime = 0;
    }
}
