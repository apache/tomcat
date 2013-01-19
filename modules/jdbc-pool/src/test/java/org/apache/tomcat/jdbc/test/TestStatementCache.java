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
package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.interceptor.StatementCache;

public class TestStatementCache extends DefaultTestCase {

    private static volatile TestStatementCacheInterceptor interceptor = null;


    @Override
    @After
    public void tearDown() throws Exception {
        interceptor = null;
        super.tearDown();
    }


    private void config(boolean cachePrepared, boolean cacheCallable, int max) {
        datasource.getPoolProperties().setJdbcInterceptors(TestStatementCacheInterceptor.class.getName()+
                "(prepared="+cachePrepared+",callable="+cacheCallable+",max="+max+")");
    }

    @Test
    public void testIsCacheEnabled() throws Exception {
        config(true,true,50);
        datasource.getConnection().close();
        Assert.assertNotNull("Interceptor was not created.", interceptor);
    }

    @Test
    public void testCacheProperties() throws Exception {
        config(true,true,50);
        datasource.getConnection().close();
        Assert.assertTrue(interceptor.isCacheCallable());
        Assert.assertTrue(interceptor.isCachePrepared());
        Assert.assertEquals(50,interceptor.getMaxCacheSize());
    }

    @Test
    public void testCacheProperties2() throws Exception {
        config(false,false,100);
        datasource.getConnection().close();
        Assert.assertFalse(interceptor.isCacheCallable());
        Assert.assertFalse(interceptor.isCachePrepared());
        Assert.assertEquals(100,interceptor.getMaxCacheSize());
    }

    @Test
    public void testPreparedStatementCache() throws Exception {
        config(true,false,100);
        Connection con = datasource.getConnection();
        PreparedStatement ps1 = con.prepareStatement("select 1");
        PreparedStatement ps2 = con.prepareStatement("select 1");
        Assert.assertEquals(0,interceptor.getCacheSize().get());
        ps1.close();
        Assert.assertTrue(ps1.isClosed());
        Assert.assertEquals(1,interceptor.getCacheSize().get());
        PreparedStatement ps3 = con.prepareStatement("select 1");
        Assert.assertEquals(0,interceptor.getCacheSize().get());
        ps2.close();
        Assert.assertTrue(ps2.isClosed());
        ps3.close();
        Assert.assertTrue(ps3.isClosed());
        Assert.assertEquals(1,interceptor.getCacheSize().get());
    }

    @Test
    public void testPreparedStatementCache2() throws Exception {
        init();
        config(false,false,100);
        Connection con = datasource.getConnection();
        PreparedStatement ps1 = con.prepareStatement("select 1");
        PreparedStatement ps2 = con.prepareStatement("select 1");
        Assert.assertEquals(0,interceptor.getCacheSize().get());
        ps1.close();
        Assert.assertTrue(ps1.isClosed());
        Assert.assertEquals(0,interceptor.getCacheSize().get());
        PreparedStatement ps3 = con.prepareStatement("select 1");
        Assert.assertEquals(0,interceptor.getCacheSize().get());
        ps2.close();
        Assert.assertTrue(ps2.isClosed());
        ps3.close();
        Assert.assertTrue(ps3.isClosed());
        Assert.assertEquals(0,interceptor.getCacheSize().get());
    }

    @Test
    public void testMaxCacheSize() throws Exception {
        init();
        config(true,false,100);
        Connection con1 = datasource.getConnection();
        Connection con2 = datasource.getConnection();
        for (int i=0; i<120; i++) {
            @SuppressWarnings("resource") // Connections are closed below
            Connection con = (i%2==0)?con1:con2;
            PreparedStatement ps = con.prepareStatement("select "+i);
            ps.close();
        }
        Assert.assertEquals(100,interceptor.getCacheSize().get());
        con1.close();
        con2.close();
    }


    public static class TestStatementCacheInterceptor extends StatementCache {
        public TestStatementCacheInterceptor() {
            TestStatementCache.interceptor = this;
        }
    }
}
