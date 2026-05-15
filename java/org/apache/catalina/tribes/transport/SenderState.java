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
package org.apache.catalina.tribes.transport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.catalina.tribes.Member;

/**
 * Tracks the delivery state of a sender associated with a cluster member.
 */
public class SenderState {

    /**
     * Sender is ready to transmit messages.
     */
    public static final int READY = 0;
    /**
     * Sender has been suspected of failure but not yet confirmed.
     */
    public static final int SUSPECT = 1;
    /**
     * Sender has failed and is unable to transmit messages.
     */
    public static final int FAILING = 2;

    /**
     * Registry of sender states for each cluster member.
     */
    protected static final ConcurrentMap<Member,SenderState> memberStates = new ConcurrentHashMap<>();

    /**
     * Returns the {@link SenderState} for the given member, creating one if it does not exist.
     *
     * @param member the cluster member
     * @return the sender state for the member
     */
    public static SenderState getSenderState(Member member) {
        return getSenderState(member, true);
    }

    /**
     * Returns the {@link SenderState} for the given member.
     *
     * @param member the cluster member
     * @param create if {@code true}, creates a new state when none exists
     * @return the sender state, or {@code null} if not found and create is {@code false}
     */
    public static SenderState getSenderState(Member member, boolean create) {
        SenderState state = memberStates.get(member);
        if (state == null && create) {
            state = new SenderState();
            SenderState current = memberStates.putIfAbsent(member, state);
            if (current != null) {
                state = current;
            }
        }
        return state;
    }

    /**
     * Removes the {@link SenderState} for the given member from the registry.
     *
     * @param member the cluster member whose state should be removed
     */
    public static void removeSenderState(Member member) {
        memberStates.remove(member);
    }


    // ----------------------------------------------------- Instance Variables

    private volatile int state;

    // ----------------------------------------------------- Constructor


    private SenderState() {
        this(READY);
    }

    private SenderState(int state) {
        this.state = state;
    }

    /**
     * Returns {@code true} if the sender is in a suspect or failing state.
     *
     * @return {@code true} if the sender is not ready
     */
    public boolean isSuspect() {
        return (state == SUSPECT) || (state == FAILING);
    }

    /**
     * Sets the sender state to suspect.
     */
    public void setSuspect() {
        state = SUSPECT;
    }

    /**
     * Returns {@code true} if the sender is ready to transmit messages.
     *
     * @return {@code true} if the sender is in the ready state
     */
    public boolean isReady() {
        return state == READY;
    }

    /**
     * Sets the sender state to ready.
     */
    public void setReady() {
        state = READY;
    }

    /**
     * Returns {@code true} if the sender has failed.
     *
     * @return {@code true} if the sender is in the failing state
     */
    public boolean isFailing() {
        return state == FAILING;
    }

    /**
     * Sets the sender state to failing.
     */
    public void setFailing() {
        state = FAILING;
    }


    // ----------------------------------------------------- Public Properties

}
