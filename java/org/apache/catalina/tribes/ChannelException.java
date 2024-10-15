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
package org.apache.catalina.tribes;

import java.util.ArrayList;

/**
 * A channel exception is thrown when an internal error happens somewhere in the channel.
 * <p>
 * When a global error happens, the cause can be retrieved using <code>getCause()</code>.
 * <p>
 * If an application is sending a message and some of the recipients fail to receive it, the application can retrieve
 * what recipients failed by using the <code>getFaultyMembers()</code> method. This way, an application will always know
 * if a message was delivered successfully or not.
 */
public class ChannelException extends Exception {
    private static final long serialVersionUID = 1L;
    /**
     * Empty list to avoid reinstantiating lists
     */
    protected static final FaultyMember[] EMPTY_LIST = new FaultyMember[0];
    /**
     * Holds a list of faulty members
     */
    private ArrayList<FaultyMember> faultyMembers = null;

    /**
     * Constructor, creates a ChannelException
     *
     * @see java.lang.Exception#Exception()
     */
    public ChannelException() {
        super();
    }

    /**
     * Constructor, creates a ChannelException with an error message
     *
     * @param message The error message
     *
     * @see java.lang.Exception#Exception(String)
     */
    public ChannelException(String message) {
        super(message);
    }

    /**
     * Constructor, creates a ChannelException with an error message and a cause
     *
     * @param message The error message
     * @param cause   Throwable
     *
     * @see java.lang.Exception#Exception(String,Throwable)
     */
    public ChannelException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor, creates a ChannelException with a cause
     *
     * @param cause Throwable
     *
     * @see java.lang.Exception#Exception(Throwable)
     */
    public ChannelException(Throwable cause) {
        super(cause);
    }

    /**
     * Returns the message for this exception
     *
     * @return the error message
     *
     * @see java.lang.Exception#getMessage()
     */
    @Override
    public String getMessage() {
        StringBuilder buf = new StringBuilder(super.getMessage());
        if (faultyMembers == null || faultyMembers.size() == 0) {
            buf.append("; No faulty members identified.");
        } else {
            buf.append("; Faulty members:");
            for (FaultyMember mbr : faultyMembers) {
                buf.append(mbr.getMember().getName());
                buf.append("; ");
            }
        }
        return buf.toString();
    }

    /**
     * Adds a faulty member, and the reason the member failed.
     *
     * @param mbr Member
     * @param x   Exception
     *
     * @return <code>true</code> if the member was added
     */
    public boolean addFaultyMember(Member mbr, Exception x) {
        return addFaultyMember(new FaultyMember(mbr, x));
    }

    /**
     * Adds a list of faulty members
     *
     * @param mbrs FaultyMember[]
     *
     * @return the number of members added
     */
    public int addFaultyMember(FaultyMember[] mbrs) {
        int result = 0;
        for (int i = 0; mbrs != null && i < mbrs.length; i++) {
            if (addFaultyMember(mbrs[i])) {
                result++;
            }
        }
        return result;
    }

    /**
     * Adds a faulty member
     *
     * @param mbr FaultyMember
     *
     * @return <code>true</code> if the member was added
     */
    public boolean addFaultyMember(FaultyMember mbr) {
        if (this.faultyMembers == null) {
            this.faultyMembers = new ArrayList<>();
        }
        if (!faultyMembers.contains(mbr)) {
            return faultyMembers.add(mbr);
        } else {
            return false;
        }
    }

    /**
     * Returns an array of members that failed and the reason they failed.
     *
     * @return FaultyMember[]
     */
    public FaultyMember[] getFaultyMembers() {
        if (this.faultyMembers == null) {
            return EMPTY_LIST;
        }
        return faultyMembers.toArray(new FaultyMember[0]);
    }

    /**
     * Represent a failure to a specific member when a message was sent to more than one member
     */
    public static class FaultyMember {
        protected final Exception cause;
        protected final Member member;

        public FaultyMember(Member mbr, Exception x) {
            this.member = mbr;
            this.cause = x;
        }

        public Member getMember() {
            return member;
        }

        public Exception getCause() {
            return cause;
        }

        @Override
        public String toString() {
            return "FaultyMember:" + member.toString();
        }

        @Override
        public int hashCode() {
            return (member != null) ? member.hashCode() : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (member == null || (!(o instanceof FaultyMember)) || (((FaultyMember) o).member == null)) {
                return false;
            }
            return member.equals(((FaultyMember) o).member);
        }
    }

}
