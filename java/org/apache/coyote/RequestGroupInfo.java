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

import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.modeler.BaseModelMBean;

/**
  * JMX artifact to aggregate data from each request processor thread.
  */
public class RequestGroupInfo extends BaseModelMBean {
    /**
     * List of request processors.
     */
    private final List<RequestInfo> processors = new ArrayList<>();
    private long deadMaxTime = 0;
    private long deadProcessingTime = 0;
    private int deadRequestCount = 0;
    private int deadErrorCount = 0;
    private long deadBytesReceived = 0;
    private long deadBytesSent = 0;

    /**
     * Default constructor.
     */
    public RequestGroupInfo() {
        super();
    }

    /**
     * Add a request processor.
     *
     * @param rp the request processor
     */
    public synchronized void addRequestProcessor(RequestInfo rp) {
        processors.add(rp);
    }

    /**
     * Remove a request processor.
     *
     * @param rp the request processor
     */
    public synchronized void removeRequestProcessor(RequestInfo rp) {
        if (rp != null) {
            if (deadMaxTime < rp.getMaxTime()) {
                deadMaxTime = rp.getMaxTime();
            }
            deadProcessingTime += rp.getProcessingTime();
            deadRequestCount += rp.getRequestCount();
            deadErrorCount += rp.getErrorCount();
            deadBytesReceived += rp.getBytesReceived();
            deadBytesSent += rp.getBytesSent();

            processors.remove(rp);
        }
    }

    /**
     * Return the maximum request processing time.
     *
     * @return the maximum time
     */
    public synchronized long getMaxTime() {
        long maxTime = deadMaxTime;
        for (RequestInfo rp : processors) {
            if (maxTime < rp.getMaxTime()) {
                maxTime = rp.getMaxTime();
            }
        }
        return maxTime;
    }

    /**
     * Reset the maximum time.
     *
     * @param maxTime the new maximum time
     */
    public synchronized void setMaxTime(long maxTime) {
        deadMaxTime = maxTime;
        for (RequestInfo rp : processors) {
            rp.setMaxTime(maxTime);
        }
    }

    /**
     * Return the total processing time.
     *
     * @return the total processing time
     */
    public synchronized long getProcessingTime() {
        long time = deadProcessingTime;
        for (RequestInfo rp : processors) {
            time += rp.getProcessingTime();
        }
        return time;
    }

    /**
     * Reset the total processing time.
     *
     * @param totalTime the new total processing time
     */
    public synchronized void setProcessingTime(long totalTime) {
        deadProcessingTime = totalTime;
        for (RequestInfo rp : processors) {
            rp.setProcessingTime(totalTime);
        }
    }

    /**
     * Return the request count.
     *
     * @return the request count
     */
    public synchronized int getRequestCount() {
        int requestCount = deadRequestCount;
        for (RequestInfo rp : processors) {
            requestCount += rp.getRequestCount();
        }
        return requestCount;
    }

    /**
     * Reset the request count.
     *
     * @param requestCount the new request count
     */
    public synchronized void setRequestCount(int requestCount) {
        deadRequestCount = requestCount;
        for (RequestInfo rp : processors) {
            rp.setRequestCount(requestCount);
        }
    }

    /**
     * Return the error count.
     *
     * @return the error count
     */
    public synchronized int getErrorCount() {
        int requestCount = deadErrorCount;
        for (RequestInfo rp : processors) {
            requestCount += rp.getErrorCount();
        }
        return requestCount;
    }

    /**
     * Reset the error count.
     *
     * @param errorCount the new error count
     */
    public synchronized void setErrorCount(int errorCount) {
        deadErrorCount = errorCount;
        for (RequestInfo rp : processors) {
            rp.setErrorCount(errorCount);
        }
    }

    /**
     * Return the total bytes received.
     *
     * @return the bytes received
     */
    public synchronized long getBytesReceived() {
        long bytes = deadBytesReceived;
        for (RequestInfo rp : processors) {
            bytes += rp.getBytesReceived();
        }
        return bytes;
    }

    /**
     * Reset the bytes received.
     *
     * @param bytesReceived the new bytes received
     */
    public synchronized void setBytesReceived(long bytesReceived) {
        deadBytesReceived = bytesReceived;
        for (RequestInfo rp : processors) {
            rp.setBytesReceived(bytesReceived);
        }
    }

    /**
     * Return the total bytes sent.
     *
     * @return the bytes sent
     */
    public synchronized long getBytesSent() {
        long bytes = deadBytesSent;
        for (RequestInfo rp : processors) {
            bytes += rp.getBytesSent();
        }
        return bytes;
    }

    /**
     * Reset the bytes sent.
     *
     * @param bytesSent the new bytes sent
     */
    public synchronized void setBytesSent(long bytesSent) {
        deadBytesSent = bytesSent;
        for (RequestInfo rp : processors) {
            rp.setBytesSent(bytesSent);
        }
    }

    /**
     * Reset all counters.
     */
    public void resetCounters() {
        this.setBytesReceived(0);
        this.setBytesSent(0);
        this.setRequestCount(0);
        this.setProcessingTime(0);
        this.setMaxTime(0);
        this.setErrorCount(0);
    }
}
