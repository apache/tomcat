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
package org.apache.tomcat.categories;

import org.junit.experimental.categories.Categories;
import org.junit.experimental.categories.Categories.IncludeCategory;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import org.apache.catalina.authenticator.TestFormAuthenticatorA;
import org.apache.catalina.authenticator.TestSSOnonLoginAndBasicAuthenticator;
import org.apache.catalina.core.TestAsyncContextStateChanges;
import org.apache.catalina.core.TestStandardContextResources;
import org.apache.catalina.core.TestStandardWrapper;
import org.apache.catalina.loader.TestVirtualContext;
import org.apache.catalina.mapper.TestMapperWebapps;
import org.apache.catalina.nonblocking.TestNonBlockingAPI;
import org.apache.catalina.servlets.TestDefaultServlet;
import org.apache.catalina.servlets.TestDefaultServletEncodingPassThroughBom;
import org.apache.catalina.servlets.TestDefaultServletEncodingWithBom;
import org.apache.catalina.servlets.TestDefaultServletEncodingWithoutBom;
import org.apache.catalina.servlets.TestDefaultServletIfMatchRequests;
import org.apache.catalina.servlets.TestDefaultServletOptions;
import org.apache.catalina.servlets.TestWebdavServletOptionsFile;
import org.apache.catalina.startup.TestContextConfig;
import org.apache.catalina.startup.TestHostConfigAutomaticDeploymentDir;
import org.apache.catalina.startup.TestHostConfigAutomaticDeploymentWar;
import org.apache.catalina.startup.TestHostConfigAutomaticDeploymentXml;
import org.apache.catalina.tribes.group.interceptors.TestTcpFailureDetector;
import org.apache.catalina.valves.TestStuckThreadDetectionValve;
import org.apache.catalina.valves.rewrite.TestRewriteValve;
import org.apache.coyote.ajp.TestAbstractAjpProcessor;
import org.apache.coyote.http11.TestHttp11InputBuffer;
import org.apache.coyote.http11.TestHttp11InputBufferCRLF;
import org.apache.coyote.http11.TestHttp11Processor;
import org.apache.coyote.http11.filters.TestChunkedInputFilter;
import org.apache.coyote.http2.TestAsync;
import org.apache.coyote.http2.TestHttp2ConnectionTimeouts;
import org.apache.coyote.http2.TestHttp2Limits;
import org.apache.coyote.http2.TestHttp2Section_6_8;
import org.apache.coyote.http2.TestStreamQueryString;
import org.apache.el.TestELInJsp;
import org.apache.jasper.compiler.TestCompiler;
import org.apache.jasper.compiler.TestEncodingDetector;
import org.apache.jasper.compiler.TestGenerator;
import org.apache.jasper.compiler.TestJspConfig;
import org.apache.jasper.compiler.TestJspDocumentParser;
import org.apache.jasper.compiler.TestValidator;
import org.apache.jasper.optimizations.TestELInterpreterTagSetters;
import org.apache.jasper.optimizations.TestStringInterpreterTagSetters;
import org.apache.jasper.runtime.TestCustomHttpJspPage;
import org.apache.jasper.runtime.TestJspContextWrapper;
import org.apache.jasper.runtime.TestJspRuntimeLibrary;
import org.apache.jasper.runtime.TestPageContextImpl;
import org.apache.jasper.servlet.TestTldScanner;
import org.apache.naming.TestEnvEntry;
import org.apache.naming.resources.TestWarDirContext;
import org.apache.tomcat.util.net.TestClientCert;
import org.apache.tomcat.util.net.TestCustomSslTrustManager;
import org.apache.tomcat.util.net.TestSSLHostConfigCompat;
import org.apache.tomcat.util.net.TestSsl;
import org.apache.tomcat.websocket.TestWebSocketFrameClientSSL;
import org.apache.tomcat.websocket.TestWsWebSocketContainer;
import org.apache.tomcat.websocket.TestWsWebSocketContainerTimeoutServer;
import org.apache.tomcat.websocket.server.TestSlowClient;
import org.apache.tomcat.websocket.server.TestWsRemoteEndpointImplServerDeadlock;

/**
 * JUnit suite that runs all tests annotated with {@link SmokeTest}.
 *
 * <p>Run via Ant: {@code ant test -Dtest.profile=smoke}
 * <p>Or directly: {@code ant test-smoke-category}
 */
@RunWith(Categories.class)
@IncludeCategory(SmokeTest.class)
@SuiteClasses({
    TestFormAuthenticatorA.class,
    TestSSOnonLoginAndBasicAuthenticator.class,
    TestAsyncContextStateChanges.class,
    TestStandardContextResources.class,
    TestStandardWrapper.class,
    TestVirtualContext.class,
    TestMapperWebapps.class,
    TestNonBlockingAPI.class,
    TestDefaultServlet.class,
    TestDefaultServletEncodingPassThroughBom.class,
    TestDefaultServletEncodingWithBom.class,
    TestDefaultServletEncodingWithoutBom.class,
    TestDefaultServletIfMatchRequests.class,
    TestDefaultServletOptions.class,
    TestWebdavServletOptionsFile.class,
    TestContextConfig.class,
    TestHostConfigAutomaticDeploymentDir.class,
    TestHostConfigAutomaticDeploymentWar.class,
    TestHostConfigAutomaticDeploymentXml.class,
    TestTcpFailureDetector.class,
    TestStuckThreadDetectionValve.class,
    TestRewriteValve.class,
    TestAbstractAjpProcessor.class,
    TestHttp11InputBuffer.class,
    TestHttp11InputBufferCRLF.class,
    TestHttp11Processor.class,
    TestChunkedInputFilter.class,
    TestAsync.class,
    TestHttp2ConnectionTimeouts.class,
    TestHttp2Limits.class,
    TestHttp2Section_6_8.class,
    TestStreamQueryString.class,
    TestELInJsp.class,
    TestCompiler.class,
    TestEncodingDetector.class,
    TestGenerator.class,
    TestJspConfig.class,
    TestJspDocumentParser.class,
    TestValidator.class,
    TestELInterpreterTagSetters.class,
    TestStringInterpreterTagSetters.class,
    TestCustomHttpJspPage.class,
    TestJspContextWrapper.class,
    TestJspRuntimeLibrary.class,
    TestPageContextImpl.class,
    TestTldScanner.class,
    TestEnvEntry.class,
    TestWarDirContext.class,
    TestClientCert.class,
    TestCustomSslTrustManager.class,
    TestSSLHostConfigCompat.class,
    TestSsl.class,
    TestWebSocketFrameClientSSL.class,
    TestWsWebSocketContainer.class,
    TestWsWebSocketContainerTimeoutServer.class,
    TestSlowClient.class,
    TestWsRemoteEndpointImplServerDeadlock.class,
})
public class SmokeSuite {
}
