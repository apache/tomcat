/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.test.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Statement oracle shared by the cross-thread-close tests: executing answers
 * TRUE (or the configured result set) while the statement is open, and fails
 * with a SQLException once another thread has closed it.
 */
public class FakeStatement implements InvocationHandler {

    private volatile boolean closed;
    private final ResultSet resultSet;

    public FakeStatement() {
        this(null);
    }

    public FakeStatement(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("execute".equals(name)) {
            if (closed) {
                throw new SQLException("statement closed by the closer thread");
            }
            return Boolean.TRUE;
        }
        if ("executeQuery".equals(name)) {
            if (closed) {
                throw new SQLException("statement closed by the closer thread");
            }
            return resultSet;
        }
        if ("close".equals(name)) {
            closed = true;
            return null;
        }
        if ("isClosed".equals(name)) {
            return Boolean.valueOf(closed);
        }
        if ("toString".equals(name)) {
            return "FakeStatement";
        }
        throw new UnsupportedOperationException(name);
    }
}
