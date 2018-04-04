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

public class XAException extends java.lang.Exception {

    private static final long serialVersionUID = -8647128647842792941L;

    public int errorCode;

    public XAException() {
        super();
    }

    public XAException(String s) {
        super(s);
    }

    public XAException(int errcode) {
        super();
        errorCode = errcode;
    }

    public static final int XA_RBBASE = 100;
    public static final int XA_RBROLLBACK = XA_RBBASE;
    public static final int XA_RBCOMMFAIL = XA_RBBASE + 1;
    public static final int XA_RBDEADLOCK = XA_RBBASE + 2;
    public static final int XA_RBINTEGRITY = XA_RBBASE + 3;
    public static final int XA_RBOTHER = XA_RBBASE + 4;
    public static final int XA_RBPROTO = XA_RBBASE + 5;
    public static final int XA_RBTIMEOUT = XA_RBBASE + 6;
    public static final int XA_RBTRANSIENT = XA_RBBASE + 7;
    public static final int XA_RBEND = XA_RBTRANSIENT;
    public static final int XA_NOMIGRATE = 9;
    public static final int XA_HEURHAZ = 8;
    public static final int XA_HEURCOM = 7;
    public static final int XA_HEURRB = 6;
    public static final int XA_HEURMIX = 5;
    public static final int XA_RETRY = 4;
    public static final int XA_RDONLY = 3;
    public static final int XAER_ASYNC = -2;
    public static final int XAER_RMERR = -3;
    public static final int XAER_NOTA = -4;
    public static final int XAER_INVAL = -5;
    public static final int XAER_PROTO = -6;
    public static final int XAER_RMFAIL = -7;
    public static final int XAER_DUPID = -8;
    public static final int XAER_OUTSIDE = -9;

}
