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
package org.apache.catalina.ha;

import org.apache.catalina.tribes.Member;


/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class ClusterMessageBase implements ClusterMessage {
    
    private static final long serialVersionUID = 1L;

    protected transient Member address;
    private String uniqueId;
    private long timestamp;
    public ClusterMessageBase() {
        // NO-OP
    }

    /**
     * getAddress
     *
     * @return Member
     * @todo Implement this org.apache.catalina.ha.ClusterMessage method
     */
    public Member getAddress() {
        return address;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * setAddress
     *
     * @param member Member
     * @todo Implement this org.apache.catalina.ha.ClusterMessage method
     */
    public void setAddress(Member member) {
        this.address = member;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
