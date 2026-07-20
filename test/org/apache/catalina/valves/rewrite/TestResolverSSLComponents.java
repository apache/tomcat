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
package org.apache.catalina.valves.rewrite;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Request;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.net.SSLSupport;
import org.easymock.EasyMock;

public class TestResolverSSLComponents {

    @Test
    public void testEscapedComma() throws Exception {
        Resolver resolver = createResolver("CN=abc\\,def,OU=xyz");

        Assert.assertEquals("abc,def", resolver.resolveSsl("SSL_CLIENT_S_DN_CN"));
        Assert.assertEquals("xyz", resolver.resolveSsl("SSL_CLIENT_S_DN_OU"));
    }


    private Resolver createResolver(String subjectDn) throws IOException {
        X509Certificate certificate = EasyMock.createMock(X509Certificate.class);
        EasyMock.expect(certificate.getSubjectX500Principal()).andStubReturn(new X500Principal(subjectDn));

        SSLSupport sslSupport = EasyMock.createMock(SSLSupport.class);
        EasyMock.expect(sslSupport.getPeerCertificateChain()).andStubReturn(new X509Certificate[] { certificate });

        Request request = EasyMock.createMock(Request.class);
        EasyMock.expect(request.getAttribute(SSLSupport.SESSION_MGR)).andStubReturn(sslSupport);

        Log log = EasyMock.createNiceMock(Log.class);
        EasyMock.replay(certificate, sslSupport, request, log);

        return new ResolverImpl(request, log);
    }
}
