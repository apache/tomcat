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
import java.util.concurrent.ScheduledExecutorService;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.MembershipProvider;
import org.apache.catalina.tribes.MembershipService;

public abstract class MembershipProviderBase implements MembershipProvider {

    protected Membership membership;
    protected MembershipListener membershipListener;
    protected MembershipService service;
    // The event notification executor
    protected ScheduledExecutorService executor;

    @Override
    public void init(Properties properties) throws Exception {
    }

    @Override
    public boolean hasMembers() {
        if (membership == null ) {
            return false;
        }
        return membership.hasMembers();
    }

    @Override
    public Member getMember(Member mbr) {
        if (membership.getMembers() == null) {
            return null;
        }
        return membership.getMember(mbr);
    }

    @Override
    public Member[] getMembers() {
        if (membership.getMembers() == null) {
            return Membership.EMPTY_MEMBERS;
        }
        return membership.getMembers();
    }

    @Override
    public void setMembershipListener(MembershipListener listener) {
        this.membershipListener = listener;
    }

    @Override
    public void setMembershipService(MembershipService service) {
        this.service = service;
        executor = service.getChannel().getUtilityExecutor();
    }
}