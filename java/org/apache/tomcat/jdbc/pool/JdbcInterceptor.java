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
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public abstract class JdbcInterceptor implements InvocationHandler {
    public  static final String CLOSE_VAL = "close";
    public  static final String TOSTRING_VAL = "toString";
    public  static final String ISCLOSED_VAL = "isClosed"; 

    private JdbcInterceptor next = null;

    public JdbcInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (getNext()!=null) return getNext().invoke(this,method,args);
        else throw new NullPointerException();
    }

    public JdbcInterceptor getNext() {
        return next;
    }

    public void setNext(JdbcInterceptor next) {
        this.next = next;
    }
    
    /**
     * Gets called each time the connection is borrowed from the pool
     * @param parent - the connection pool owning the connection
     * @param con - the pooled connection
     */
    public abstract void reset(ConnectionPool parent, PooledConnection con);
}
