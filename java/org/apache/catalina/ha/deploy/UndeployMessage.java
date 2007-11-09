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

package org.apache.catalina.ha.deploy;

import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.tribes.Member;
import java.io.Serializable;
public class UndeployMessage implements ClusterMessage,Serializable {
    private Member address;
    private long timestamp;
    private String uniqueId;
    private String contextPath;
    private boolean undeploy;
    private int resend = 0;
    private int compress = 0;

    public UndeployMessage() {} //for serialization
    public UndeployMessage(Member address,
                           long timestamp,
                           String uniqueId,
                           String contextPath,
                           boolean undeploy) {
        this.address  = address;
        this.timestamp= timestamp;
        this.undeploy = undeploy;
        this.uniqueId = uniqueId;
        this.undeploy = undeploy;
        this.contextPath = contextPath;
    }

    public Member getAddress() {
        return address;
    }

    public void setAddress(Member address) {
        this.address = address;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public boolean getUndeploy() {
        return undeploy;
    }

    public void setUndeploy(boolean undeploy) {
        this.undeploy = undeploy;
    }
    /**
     * @return Returns the compress.
     * @since 5.5.10 
     */
    public int getCompress() {
        return compress;
    }
    /**
     * @param compress The compress to set.
     * @since 5.5.10
     */
    public void setCompress(int compress) {
        this.compress = compress;
    }
    /**
     * @return Returns the resend.
     * @since 5.5.10
     */
    public int getResend() {
        return resend;
    }
    /**
     * @param resend The resend to set.
     * @since 5.5.10
     */
    public void setResend(int resend) {
        this.resend = resend;
    }

}
