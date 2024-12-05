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
package org.apache.catalina.valves;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.compat.JreCompat;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class TestJDBCAccessLogValve extends TomcatBaseTest {

    public static final String SCHEMA =
            "CREATE TABLE access (\n" +
            "  id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY(Start with 1, Increment by 1),\n" +
            "  remoteHost CHAR(15) NOT NULL,\n" +
            "  userName CHAR(15),\n" +
            "  timestamp TIMESTAMP NOT NULL,\n" +
            "  query VARCHAR(255),\n" +
            "  status SMALLINT NOT NULL,\n" +
            "  bytes INT NOT NULL,\n" +
            "  virtualHost VARCHAR(64),\n" +
            "  method VARCHAR(8),\n" +
            "  referer VARCHAR(128),\n" +
            "  userAgent VARCHAR(128)\n" +
            ")";

    @Parameterized.Parameters(name = "{index}: logPattern[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] {"common"});
        parameterSets.add(new Object[] {"combined"});

        return parameterSets;
    }

    @Parameter(0)
    public String logPattern;

    @Test
    public void testValve() throws Exception {

        Assume.assumeTrue(JreCompat.isJre16Available());

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        getTomcatInstanceTestWebapp(false, false);
        Context ctx = (Context) tomcat.getHost().findChildren()[0];

        CustomJDBCAccessLogValve accessLogValve = new CustomJDBCAccessLogValve();
        accessLogValve.setDriverName("org.apache.derby.jdbc.EmbeddedDriver");
        accessLogValve.setConnectionURL("jdbc:derby:" + getTemporaryDirectory().getAbsolutePath()
                + "/accesslog-" + logPattern + ";create=true");
        accessLogValve.setPattern(logPattern);
        ctx.getParent().getPipeline().addValve(accessLogValve);

        tomcat.start();

        ByteChunk result = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/index.html", result, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        result.recycle();

        rc = getUrl("http://localhost:" + getPort() + "/test/404.html?foo=bar", result, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        accessLogValve.verify();

        tomcat.stop();

    }

    protected class CustomJDBCAccessLogValve extends JDBCAccessLogValve {
        @Override
        protected void prepare() throws SQLException {
            try (Statement statement = conn.createStatement()) {
                statement.execute(SCHEMA);
            }
        }

        public void verify() throws Exception {
            /*
             * The access log is written after the response. The client may have processed the response faster than the
             * server was able to write the access log. Give the server up to 5 seconds to write the access log.
             */
            int rowCount = 0;
            int timerCount = 0;
            while (timerCount < 500) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute("SELECT COUNT(*) FROM access");
                    ResultSet rs = statement.getResultSet();
                    Assert.assertTrue(rs.next());
                    rowCount = rs.getInt(1);
                    if (rowCount > 1) {
                        break;
                    }
                    Thread.sleep(10);
                    timerCount++;
                }
            }
            try (Statement statement = conn.createStatement()) {
                statement.execute("SELECT * FROM access");
                ResultSet rs = statement.getResultSet();
                Assert.assertTrue(rs.next());
                Assert.assertEquals(HttpServletResponse.SC_OK, rs.getInt("status"));
                Assert.assertEquals("/test/index.html", rs.getString("query"));
                if (System.lineSeparator().equals(SimpleHttpClient.CRLF)) {
                    Assert.assertEquals(957, rs.getLong("bytes"));
                } else {
                    Assert.assertEquals(934, rs.getLong("bytes"));
                }
                Assert.assertTrue(rs.next());
                Assert.assertEquals(HttpServletResponse.SC_OK, rs.getInt("status"));
                Assert.assertEquals("/test/404.html", rs.getString("query"));
            }
        }
    }
}
