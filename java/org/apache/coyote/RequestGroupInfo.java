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

/** This can be moved to top level ( eventually with a better name ).
 *  It is currently used only as a JMX artifact, to aggregate the data
 *  collected from each RequestProcessor thread.
 */
public class RequestGroupInfo {
    private final ArrayList<RequestInfo> processors = new ArrayList<>();
    private long deadMaxTime = 0;
    private long deadProcessingTime = 0;
    private int deadRequestCount = 0;
    private int deadErrorCount = 0;
    private long deadBytesReceived = 0;
    private long deadBytesSent = 0;

    public synchronized void addRequestProcessor( RequestInfo rp ) {
        processors.add( rp );
    }

    public synchronized void removeRequestProcessor( RequestInfo rp ) {
        if( rp != null ) {
            if( deadMaxTime < rp.getMaxTime() )
                deadMaxTime = rp.getMaxTime();
            deadProcessingTime += rp.getProcessingTime();
            deadRequestCount += rp.getRequestCount();
            deadErrorCount += rp.getErrorCount();
            deadBytesReceived += rp.getBytesReceived();
            deadBytesSent += rp.getBytesSent();

            processors.remove( rp );
        }
    }

    public synchronized long getMaxTime() {
        long maxTime = deadMaxTime;
        for (RequestInfo rp : processors) {
            if (maxTime < rp.getMaxTime()) {
                maxTime=rp.getMaxTime();
            }
        }
        return maxTime;
    }

    // Used to reset the times
    public synchronized void setMaxTime(long maxTime) {
        deadMaxTime = maxTime;
        for (RequestInfo rp : processors) {
            rp.setMaxTime(maxTime);
        }
    }

    public synchronized long getProcessingTime() {
        long time = deadProcessingTime;
        for (RequestInfo rp : processors) {
            time += rp.getProcessingTime();
        }
        return time;
    }

    public synchronized void setProcessingTime(long totalTime) {
        deadProcessingTime = totalTime;
        for (RequestInfo rp : processors) {
            rp.setProcessingTime( totalTime );
        }
    }

    public synchronized int getRequestCount() {
        int requestCount = deadRequestCount;
        for (RequestInfo rp : processors) {
            requestCount += rp.getRequestCount();
        }
        return requestCount;
    }

    public synchronized void setRequestCount(int requestCount) {
        deadRequestCount = requestCount;
        for (RequestInfo rp : processors) {
            rp.setRequestCount( requestCount );
        }
    }

    public synchronized int getErrorCount() {
        int requestCount = deadErrorCount;
        for (RequestInfo rp : processors) {
            requestCount += rp.getErrorCount();
        }
        return requestCount;
    }

    public synchronized void setErrorCount(int errorCount) {
        deadErrorCount = errorCount;
        for (RequestInfo rp : processors) {
            rp.setErrorCount( errorCount);
        }
    }

    public synchronized long getBytesReceived() {
        long bytes = deadBytesReceived;
        for (RequestInfo rp : processors) {
            bytes += rp.getBytesReceived();
        }
        return bytes;
    }

    public synchronized void setBytesReceived(long bytesReceived) {
        deadBytesReceived = bytesReceived;
        for (RequestInfo rp : processors) {
            rp.setBytesReceived( bytesReceived );
        }
    }

    public synchronized long getBytesSent() {
        long bytes=deadBytesSent;
        for (RequestInfo rp : processors) {
            bytes += rp.getBytesSent();
        }
        return bytes;
    }

    public synchronized void setBytesSent(long bytesSent) {
        deadBytesSent = bytesSent;
        for (RequestInfo rp : processors) {
            rp.setBytesSent( bytesSent );
        }
    }

    public void resetCounters() {
        this.setBytesReceived(0);
        this.setBytesSent(0);
        this.setRequestCount(0);
        this.setProcessingTime(0);
        this.setMaxTime(0);
        this.setErrorCount(0);
    }
}
