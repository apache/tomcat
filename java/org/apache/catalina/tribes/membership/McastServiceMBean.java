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
package org.apache.catalina.tribes.membership;

import java.util.Properties;

import org.apache.catalina.tribes.Member;

public interface McastServiceMBean {

    // Attributes
    public String getAddress();

    public int getPort();

    public long getFrequency();

    public long getDropTime();

    public String getBind();

    public int getTtl();

    public byte[] getDomain();

    public int getSoTimeout();

    public boolean getRecoveryEnabled();

    public int getRecoveryCounter();

    public long getRecoverySleepTime();

    public boolean getLocalLoopbackDisabled();

    public String getLocalMemberName();

    // Operation
    public Properties getProperties();

    public boolean hasMembers();

    public String[] getMembersByName();

    public Member findMemberByName(String name);
}
