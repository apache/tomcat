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
package org.apache.tomcat.util.net.jsse.openssl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;

public class TesterOpenSSL {

    private TesterOpenSSL() {
        // Utility class. Hide default constructor.
    }


    public static Set<String> getOpenSSLCiphersAsSet(String specification) throws Exception {
        String[] ciphers = getOpenSSLCiphersAsExpression(specification).trim().split(":");
        Set<String> result = new HashSet<>(ciphers.length);
        for (String cipher : ciphers) {
            result.add(cipher);
        }
        return result;

    }


    public static String getOpenSSLCiphersAsExpression(String specification) throws Exception {
        String openSSLPath = System.getProperty("tomcat.test.openssl.path");
        if (openSSLPath == null || openSSLPath.length() == 0) {
            openSSLPath = "openssl";
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(openSSLPath);
        cmd.add("ciphers");
        if (specification != null) {
            cmd.add(specification);
        }
        Process process = Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]));
        InputStream stderr = process.getErrorStream();
        InputStream stdout = process.getInputStream();

        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        IOTools.flow(stderr, stderrBytes);
        //String errorText = stderrBytes.toString();
        //Assert.assertTrue(errorText, errorText.length() == 0);

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        IOTools.flow(stdout, stdoutBytes);
        return stdoutBytes.toString();
    }



}
