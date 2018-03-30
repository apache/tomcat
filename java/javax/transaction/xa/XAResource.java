/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.transaction.xa;

public interface XAResource {
    void commit(Xid xid, boolean onePhase) throws XAException;

    void end(Xid xid, int flags) throws XAException;

    void forget(Xid xid) throws XAException;

    int getTransactionTimeout() throws XAException;

    boolean isSameRM(XAResource xares) throws XAException;

    int prepare(Xid xid) throws XAException;

    Xid[] recover(int flag) throws XAException;

    void rollback(Xid xid) throws XAException;

    boolean setTransactionTimeout(int seconds) throws XAException;

    void start(Xid xid, int flags) throws XAException;

    public static final int TMENDRSCAN = 0x00800000;
    public static final int TMFAIL = 0x20000000;
    public static final int TMJOIN = 0x00200000;
    public static final int TMNOFLAGS = 0x00000000;
    public static final int TMONEPHASE = 0x40000000;
    public static final int TMRESUME = 0x08000000;
    public static final int TMSTARTRSCAN = 0x01000000;
    public static final int TMSUCCESS = 0x04000000;
    public static final int TMSUSPEND = 0x02000000;
    public static final int XA_RDONLY = 0x00000003;
    public static final int XA_OK = 0;

}
