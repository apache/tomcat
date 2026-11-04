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
package org.apache.catalina.tribes.group.interceptors;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.AbsoluteOrder;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;

/**
 * A dinky coordinator, just uses a sorted version of the member array.
 */
public class SimpleCoordinator extends ChannelInterceptorBase {

    /**
     * Creates a new SimpleCoordinator instance.
     */
    public SimpleCoordinator() {
        // NO-OP
    }

    private volatile Member[] view;

    private final AtomicBoolean membershipChanged = new AtomicBoolean();

    private void membershipChanged() {
        membershipChanged.set(true);
    }

    @Override
    public void memberAdded(final Member member) {
        super.memberAdded(member);
        membershipChanged();
        installViewWhenStable();
    }

    @Override
    public void memberDisappeared(final Member member) {
        super.memberDisappeared(member);
        membershipChanged();
        installViewWhenStable();
    }

    /**
     * Override to receive view changes. The callback is invoked while holding the monitor of
     * this interceptor, so implementations must not block for long or acquire external or
     * channel locks. The view array must not be modified by the callback.
     *
     * @param view The members array
     */
    protected void viewChange(final Member[] view) {
    }

    @Override
    public void start(int svc) throws ChannelException {
        super.start(svc);
        installViewWhenStable();
    }

    private void installViewWhenStable() {
        int stableCount = 0;

        while (stableCount < 10) {
            if (membershipChanged.compareAndSet(true, false)) {
                stableCount = 0;
            } else {
                stableCount++;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(250);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        final Member[] members = getMembers();
        final Member[] newView = new Member[members.length + 1];
        System.arraycopy(members, 0, newView, 0, members.length);
        newView[members.length] = getLocalMember(false);
        Arrays.sort(newView, AbsoluteOrder.comp);
        // The view is published from the membership callback threads while the
        // accessors below are called from arbitrary threads. The lock keeps the
        // comparison, the publication and the view change notification atomic,
        // so that concurrent installs of the same view only notify once and
        // viewChange observers see the views in install order
        synchronized (this) {
            if (Arrays.equals(view, newView)) {
                return;
            }
            view = newView;
            viewChange(newView);
        }
    }

    @Override
    public void stop(int svc) throws ChannelException {
        super.stop(svc);
    }

    /**
     * Returns the current sorted view of cluster members.
     *
     * @return the current member view, or {@code null} if not yet established
     */
    public Member[] getView() {
        return view;
    }

    /**
     * Returns the current coordinator member (first member in the sorted view).
     *
     * @return the coordinator member, or {@code null} if no view is established
     */
    public Member getCoordinator() {
        final Member[] current = view;
        return current == null ? null : current[0];
    }

    /**
     * Returns whether this member is the current coordinator.
     *
     * @return {@code true} if this member is the coordinator
     */
    public boolean isCoordinator() {
        final Member[] current = view;
        return current != null && getLocalMember(false).equals(current[0]);
    }

}
