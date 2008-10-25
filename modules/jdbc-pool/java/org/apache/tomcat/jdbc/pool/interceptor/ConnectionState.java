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

import java.lang.reflect.Method;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * Interceptor that keep track of connection state to avoid roundtrips to the database
 * @author fhanik
 *
 */

public class ConnectionState extends JdbcInterceptor  {

    protected final String[] readState = {"getAutoCommit","getTransactionIsolation","isReadOnly"};
    protected final String[] writeState = {"setAutoCommit","setTransactionIsolation","setReadOnly"};

    protected Boolean autoCommit = null;
    protected Integer transactionIsolation = null;
    protected Boolean readOnly = null;

    public void reset(ConnectionPool parent, PooledConnection con) {
        autoCommit = null;
        transactionIsolation = null;
        readOnly = null;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        boolean read = false;
        int index = -1;
        for (int i=0; (!read) && i<readState.length; i++) {
            read = name==readState[i];
            if (read) index = i;
        }
        boolean write = false;
        for (int i=0; (!write) && (!read) && i<writeState.length; i++) {
            write = name==writeState[i];
            if (write) index = i;
        }
        Object result = null;
        if (read) {
            switch (index) {
                case 0:{result = autoCommit; break;}
                case 1:{result = transactionIsolation; break;}
                case 2:{result = readOnly; break;}
                default: result = null;
            }
            //return cached result, if we have it
            if (result!=null) return result;
        }

        result = super.invoke(proxy, method, args);
        if (read || write) {
            switch (index) {
                case 0:{autoCommit = (Boolean) (read?result:args[0]); break;}
                case 1:{transactionIsolation = (Integer)(read?result:args[0]); break;}
                case 2:{readOnly = (Boolean)(read?result:args[0]); break;}
            }
        }
        return result;
    }

}
