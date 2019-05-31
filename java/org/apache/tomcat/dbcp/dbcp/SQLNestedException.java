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

package org.apache.tomcat.dbcp.dbcp;

import java.sql.SQLException;

/**
 * A SQLException subclass containing another Throwable
 *
 * @author Dirk Verbeeck
 * @deprecated Use '(SQLException) new SQLException(msg).initCause(e)' instead; this class will be removed in DBCP 2.0
 */
@Deprecated
public class SQLNestedException extends SQLException {

    private static final long serialVersionUID = 1046151479543081202L;

    /**
     * Constructs a new <code>SQLNestedException</code> with specified
     * detail message and nested <code>Throwable</code>.
     *
     * @param msg    the error message
     * @param cause  the exception or error that caused this exception to be
     * thrown
     */
    public SQLNestedException(String msg, Throwable cause) {
        super(msg);
        if (cause != null){
            initCause(cause);
        }
    }
}
