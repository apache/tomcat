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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

/**
 * Interceptor that sets a query timeout on every created {@link Statement}.
 */
public class QueryTimeoutInterceptor extends AbstractCreateStatementInterceptor {
    private static final Log log = LogFactory.getLog(QueryTimeoutInterceptor.class);

    /**
     * Query timeout value in seconds.
     */
    int timeout = 1;

    /**
     * Default constructor.
     */
    public QueryTimeoutInterceptor() {
        super();
    }

    /**
     * Initializes the {@code queryTimeout} property from the provided configuration map.
     *
     * @param properties map of interceptor properties
     */
    @Override
    public void setProperties(Map<String,InterceptorProperty> properties) {
        super.setProperties(properties);
        InterceptorProperty p = properties.get("queryTimeout");
        if (p!=null) {
            timeout = p.getValueAsInt(timeout);
        }
    }

    /**
     * Sets the query timeout on the created statement if it is a {@link Statement} and the timeout value is positive.
     *
     * @param proxy     the actual proxy object
     * @param method    the method that was called
     * @param args      the arguments to the method
     * @param statement the statement that the underlying connection created
     * @param time      elapsed time in milliseconds
     * @return the (possibly wrapped) statement object
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        if (statement instanceof Statement && timeout > 0) {
            Statement s = (Statement)statement;
            try {
                s.setQueryTimeout(timeout);
            }catch (SQLException x) {
                log.warn("[QueryTimeoutInterceptor] Unable to set query timeout:"+x.getMessage(),x);
            }
        }
        return statement;
    }

    /**
     * No-op; this interceptor holds no state that requires cleanup on close.
     */
    @Override
    public void closeInvoked() {
    }

}
