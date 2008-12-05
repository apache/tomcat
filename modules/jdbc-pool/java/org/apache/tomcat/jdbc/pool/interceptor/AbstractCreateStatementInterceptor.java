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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.SQLException;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public abstract class  AbstractCreateStatementInterceptor extends JdbcInterceptor {
    public static final String[] statements = {"createStatement","prepareStatement","prepareCall"};
    public static final String[] executes = {"execute","executeQuery","executeUpdate","executeBatch"};

    public  AbstractCreateStatementInterceptor() {
        super();
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (compare(CLOSE_VAL,method)) {
            closeInvoked();
            return super.invoke(proxy, method, args);
        } else {
            boolean process = false;
            process = process(statements, method, process);
            if (process) {
                Object statement = super.invoke(proxy,method,args);
                return createStatement(proxy,method,args,statement);
            } else {
                return super.invoke(proxy,method,args);
            }
        }
    }
    
    /**
     * This method should return a wrapper object around a
     * java.sql.Statement, java.sql.PreparedStatement or java.sql.CallableStatement
     * @param proxy
     * @param method
     * @param args
     * @param statement
     * @return
     */
    public abstract Object createStatement(Object proxy, Method method, Object[] args, Object statement);
    
    public abstract void closeInvoked();

    protected boolean process(String[] names, Method method, boolean process) {
        final String name = method.getName();
        for (int i=0; (!process) && i<names.length; i++) {
            process = compare(names[i],name);
        }
        return process;
    }
    
    public void reset(ConnectionPool parent, PooledConnection con) {

    }
}
