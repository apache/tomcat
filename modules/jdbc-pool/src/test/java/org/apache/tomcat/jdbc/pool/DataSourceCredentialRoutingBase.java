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
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A data-source-backed pool must route a configured username or password to
 * the matching {@code getConnection(String, String)} overload even when the
 * other credential is absent, just as the driver-backed route passes the
 * credentials it has independently.
 * <p>The five former standalone methods are now one parameterized test. The
 * parameter label keeps the original scenario name, so failures still name
 * the exact path.</p>
 */
abstract class DataSourceCredentialRoutingBase {

    /** One routing scenario: provider kind, credentials and expected overload. */
    private static final class Scenario {
        final String label;
        final String providerKind;
        final String username;
        final String password;
        final boolean credentialOverload;
        final String expectedMethod;

        Scenario(String label, String providerKind, String username,
                String password, boolean credentialOverload, String expectedMethod) {
            this.label = label;
            this.providerKind = providerKind;
            this.username = username;
            this.password = password;
            this.credentialOverload = credentialOverload;
            this.expectedMethod = expectedMethod;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Arguments> credentialRoutingScenarios() {
        return Stream.of(
                Arguments.of(new Scenario(
                        "aUsernameWithoutPasswordUsesTheCredentialOverload",
                        "driver", "alice", null, true, "getConnection")),
                Arguments.of(new Scenario(
                        "aUsernameWithoutPasswordUsesTheXaCredentialOverload",
                        "xa", "alice", null, true, "getXAConnection")),
                Arguments.of(new Scenario(
                        "aUsernameWithoutPasswordUsesTheCpdsCredentialOverload",
                        "cpds", "alice", null, true, "getPooledConnection")),
                Arguments.of(new Scenario(
                        "aPasswordWithoutUsernameUsesTheCredentialOverload",
                        "driver", null, "secret", true, "getConnection")),
                Arguments.of(new Scenario(
                        "neitherCredentialUsesTheNoArgumentOverload",
                        "driver", null, null, false, "getConnection")));
    }

    /** Records which overload of the provider was called, then fails. */
    private static final class Recorder implements InvocationHandler {
        final AtomicBoolean noArg = new AtomicBoolean();
        final AtomicBoolean withArgs = new AtomicBoolean();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("getConnection") || name.equals("getXAConnection")
                    || name.equals("getPooledConnection")) {
                if (args == null || args.length == 0) {
                    noArg.set(true);
                } else {
                    withArgs.set(true);
                }
                // Recording is enough: the test observes the chosen overload.
                throw new SQLException("recorded " + name + " call");
            }
            if ("getLogWriter".equals(name)) {
                return null;
            }
            if ("getLoginTimeout".equals(name)) {
                return Integer.valueOf(0);
            }
            if ("getParentLogger".equals(name)) {
                throw new java.sql.SQLFeatureNotSupportedException();
            }
            return null;
        }
    }

    private static Object provider(String kind, Recorder recorder) {
        Class<?> iface;
        switch (kind) {
            case "xa":
                iface = javax.sql.XADataSource.class;
                break;
            case "cpds":
                iface = javax.sql.ConnectionPoolDataSource.class;
                break;
            default:
                iface = javax.sql.DataSource.class;
                break;
        }
        return Proxy.newProxyInstance(
                DataSourceCredentialRoutingBase.class.getClassLoader(),
                new Class<?>[] { iface }, recorder);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("credentialRoutingScenarios")
    void routesCredentialsToTheMatchingOverload(Scenario scenario) throws Exception {
        Recorder recorder = new Recorder();
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(provider(scenario.providerKind, recorder));
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setUsername(scenario.username);
        ds.getPoolProperties().setPassword(scenario.password);
        ds.createPool();
        try {
            Assertions.assertThrows(SQLException.class, ds::getConnection);
            if (scenario.credentialOverload) {
                Assertions.assertTrue(recorder.withArgs.get(),
                        "the " + scenario.expectedMethod
                                + " credential overload must be used for " + scenario.label);
                Assertions.assertFalse(recorder.noArg.get(),
                        "the no-argument overload must not be used when credentials are configured for "
                                + scenario.label);
            } else {
                Assertions.assertTrue(recorder.noArg.get(),
                        "with no credentials the no-argument overload must be used for "
                                + scenario.label);
                Assertions.assertFalse(recorder.withArgs.get(),
                        "the credential overload must not be used with no credentials for "
                                + scenario.label);
            }
        } finally {
            ds.close(true);
        }
    }
}
