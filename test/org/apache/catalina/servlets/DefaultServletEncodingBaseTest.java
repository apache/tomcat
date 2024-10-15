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
package org.apache.catalina.servlets;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.DefaultServlet.BomConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.parser.MediaType;

/*
 * Note: This test is split using two base classes. This is because, as a single
 *       test, it takes so long to run it dominates the time taken to run the
 *       tests when running tests using multiple threads. For example, on a
 *       system with 12 cores, the tests take ~5 minutes per connector with this
 *       test as a single test and ~3.5 minutes per connector with this test
 *       split in two.
 */
@RunWith(Parameterized.class)
public abstract class DefaultServletEncodingBaseTest extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: contextEnc[{0}], fileEnc[{1}], target[{2}]," +
            " useInclude[{3}], outputEnc[{4}], callSetCharacterEnc[{5}], useWriter[{6}]")
    public static Collection<Object[]> parameters() {

        String[] encodings = new String[] {
                "utf-8", "ibm850", "cp1252", "iso-8859-1" };

        String[] targetFiles = new String[] {
                "cp1252", "ibm850", "iso-8859-1", "utf-8-bom", "utf-8" };

        List<Object[]> parameterSets = new ArrayList<>();

        for (String contextResponseEncoding : encodings) {
            for (String fileEncoding : encodings) {
                for (String targetFile : targetFiles) {
                    for (Boolean useInclude : booleans) {
                        if (useInclude.booleanValue()) {
                            for (String outputEncoding : encodings) {
                                for (Boolean callSetCharacterEncoding : booleans) {
                                    for (Boolean useWriter : booleans) {
                                        parameterSets.add(new Object[] { contextResponseEncoding,
                                                fileEncoding, targetFile,
                                                useInclude, outputEncoding,
                                                callSetCharacterEncoding, useWriter });
                                    }
                                }
                            }
                        } else {
                            /*
                             * Not using include so ignore outputEncoding,
                             * callSetCharacterEncoding and useWriter
                             *
                             * Tests that do not use include are always expected to
                             * pass.
                             */
                            String encoding = targetFile;
                            if (encoding.endsWith("-bom")) {
                                encoding = encoding.substring(0, encoding.length() - 4);
                            }
                            parameterSets.add(new Object[] { contextResponseEncoding, fileEncoding,
                                    targetFile, useInclude, encoding, Boolean.FALSE,
                                    Boolean.FALSE });
                        }
                    }
                }
            }
        }

        return parameterSets;
    }


    private static boolean getExpected(String fileEncoding, BomConfig bomConfig, String targetFile,
            String outputEncoding, boolean callSetCharacterEncoding, boolean useWriter) {
        if (targetFile.endsWith("-bom") && !bomConfig.stripBom) {
            /*
             * If the target file contains a BOM and the BOM is not stripped the
             * test should always fail because BOM will be kept untouched
             * and may result in a different character in the output
             *
             * We could differentiate iso-8859-1 and cp1252 but we would need logic here and in
             * the asserts and that would make the logic in the test as complex as the code
             * under test.
             */
            return false;
        } else if (useWriter || callSetCharacterEncoding) {
            /*
             * Using a writer or setting the output character encoding means the
             * response will specify a character set. These cases therefore
             * reduce to can the file be read with the correct encoding.
             * (Assuming any BOM is always skipped in the included output.)
             */
            if (targetFile.endsWith("-bom") && bomConfig.useBomEncoding ||
                    targetFile.startsWith(fileEncoding) ||
                    targetFile.equals("cp1252") && fileEncoding.equals("iso-8859-1") ||
                    targetFile.equals("iso-8859-1") && fileEncoding.equals("cp1252")) {
                return true;
            } else {
                return false;
            }
        } else if (!(targetFile.startsWith(outputEncoding) ||
                targetFile.equals("cp1252") && outputEncoding.equals("iso-8859-1") ||
                targetFile.equals("iso-8859-1") && outputEncoding.equals("cp1252"))) {
            /*
             * The non-writer use cases read the target file as bytes. These
             * cases therefore reduce to can the bytes from the target file be
             * included in the output without corruption? The character used in
             * the tests has been chosen so that, apart from iso-8859-1 and
             * cp1252, the bytes vary by character set.
             * (Assuming any BOM is always skipped in the included output.)
             */
            return false;
        } else {
            return true;
        }
    }


    @Parameter(0)
    public String contextResponseEncoding;
    @Parameter(1)
    public String fileEncoding;
    @Parameter(2)
    public String targetFile;
    @Parameter(3)
    public boolean useInclude;
    @Parameter(4)
    public String outputEncoding;
    @Parameter(5)
    public boolean callSetCharacterEncoding;
    @Parameter(6)
    public boolean useWriter;


    protected abstract BomConfig getUseBom();


    @Test
    public void testEncoding() throws Exception {

        boolean expectedPass = getExpected(fileEncoding, getUseBom(), targetFile, outputEncoding,
                callSetCharacterEncoding, useWriter);

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        ctxt.setResponseCharacterEncoding(contextResponseEncoding);
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        defaultServlet.addInitParameter("fileEncoding", fileEncoding);
        defaultServlet.addInitParameter("useBomIfPresent", getUseBom().configurationValue);

        ctxt.addServletMappingDecoded("/", "default");

        if (useInclude) {
            Tomcat.addServlet(ctxt, "include", new EncodingServlet(
                    outputEncoding, callSetCharacterEncoding, targetFile, useWriter));
            ctxt.addServletMappingDecoded("/include", "include");
        }

        tomcat.start();

        final ByteChunk res = new ByteChunk();
        Map<String,List<String>> headers = new HashMap<>();

        String target;
        if (useInclude) {
            target = "http://localhost:" + getPort() + "/include";
        } else {
            target = "http://localhost:" + getPort() + "/bug49nnn/bug49464-" + targetFile + ".txt";
        }
        int rc = getUrl(target, res, headers);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String contentType = getSingleHeader("Content-Type", headers);
        if (contentType != null) {
            MediaType mediaType = MediaType.parseMediaType(new StringReader(contentType));
            String charset = mediaType.getCharset();
            if (charset == null) {
                res.setCharset(B2CConverter.getCharset(outputEncoding));
            } else {
                res.setCharset(B2CConverter.getCharset(charset));
            }
        } else {
            res.setCharset(B2CConverter.getCharset(outputEncoding));
        }
        String body = res.toString();
        /*
         * Remove BOM before checking content if DefaultServlet is configured to
         * remove BOM.
         */
        if (!useInclude && targetFile.endsWith("-bom") && getUseBom().stripBom) {
            body = body.substring(1);
        }

        if (expectedPass) {
            if (useInclude) {
                Assert.assertEquals("\u00bd-\u00bd-\u00bd", body);
            } else {
                Assert.assertEquals("\u00bd", body);
            }
        } else {
            if (useInclude) {
                Assert.assertNotEquals("\u00bd-\u00bd-\u00bd", body);
            } else {
                Assert.assertNotEquals("\u00bd", body);
            }
        }
    }


    private static class EncodingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final String outputEncoding;
        private final boolean callSetCharacterEncoding;
        private final String includeTarget;
        private final boolean useWriter;

        EncodingServlet(String outputEncoding, boolean callSetCharacterEncoding,
                String includeTarget, boolean useWriter) {
            this.outputEncoding = outputEncoding;
            this.callSetCharacterEncoding = callSetCharacterEncoding;
            this.includeTarget = includeTarget;
            this.useWriter = useWriter;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            if (callSetCharacterEncoding) {
                resp.setCharacterEncoding(outputEncoding);
            }
            if (useWriter) {
                PrintWriter pw = resp.getWriter();
                pw.print("\u00bd-");
            } else {
                resp.getOutputStream().write("\u00bd-".getBytes(outputEncoding));
            }
            resp.flushBuffer();
            RequestDispatcher rd =
                    req.getRequestDispatcher("/bug49nnn/bug49464-" + includeTarget + ".txt");
            rd.include(req, resp);
            if (useWriter) {
                PrintWriter pw = resp.getWriter();
                pw.print("-\u00bd");
            } else {
                resp.getOutputStream().write("-\u00bd".getBytes(outputEncoding));
            }
        }
    }
}
