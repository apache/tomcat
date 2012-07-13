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

public class UndeployMessage implements ClusterMessage {
    private static final long serialVersionUID = 2L;

    private Member address;
    private long timestamp;
    private String uniqueId;
    private final String contextName;

    public UndeployMessage(Member address,
                           long timestamp,
                           String uniqueId,
                           String contextName) {
        this.address  = address;
        this.timestamp= timestamp;
        this.uniqueId = uniqueId;
        this.contextName = contextName;
    }

    @Override
    public Member getAddress() {
        return address;
    }

    @Override
    public void setAddress(Member address) {
        this.address = address;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    public String getContextName() {
        return contextName;
    }
}
