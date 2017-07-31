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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.parser.MediaType;

@RunWith(Parameterized.class)
public class TestDefaultServletEncoding extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: contextEnc[{0}], fileEnc[{1}], useBom[{2}],"
            + " target[{3}], useInclude[{4}], outputEnc[{5}], callSetCharacterEnc[{6}],"
            + " useWriter[{7}], expectedPass[{8}]")
    public static Collection<Object[]> parameters() {

        String[] encodings = new String[] {
                "utf-8", "ibm850", "cp1252", "iso-8859-1" };

        String[] targetFiles = new String[] {
                "cp1252", "ibm850", "iso-8859-1", "utf-8-bom", "utf-8" };

        Boolean[] booleans = new Boolean[] { Boolean.FALSE, Boolean.TRUE };

        List<Object[]> parameterSets = new ArrayList<>();

        for (String contextResponseEncoding : encodings) {
            for (String fileEncoding : encodings) {
                for (Boolean useBom : booleans) {
                    for (String targetFile : targetFiles) {
                        for (Boolean useInclude : booleans) {
                            if (useInclude.booleanValue()) {
                                for (String outputEncoding : encodings) {
                                    for (Boolean callSetCharacterEncoding : booleans) {
                                        for (Boolean useWriter : booleans) {
                                            Boolean expected = getExpected(fileEncoding, useBom,
                                                    targetFile, outputEncoding,
                                                    callSetCharacterEncoding, useWriter);

                                            parameterSets.add(new Object[] {
                                                    contextResponseEncoding, fileEncoding, useBom,
                                                    targetFile, useInclude, outputEncoding,
                                                    callSetCharacterEncoding, useWriter, expected
                                            });
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
                                parameterSets.add(new Object[] { contextResponseEncoding,
                                        fileEncoding, useBom, targetFile, useInclude, encoding,
                                        Boolean.FALSE, Boolean.FALSE, Boolean.TRUE });
                            }
                        }
                    }
                }
            }
        }

        return parameterSets;
    }


    private static Boolean getExpected(String fileEncoding, Boolean useBom, String targetFile,
            String outputEncoding, Boolean callSetCharacterEncoding, Boolean useWriter) {
        if (useWriter.booleanValue() || callSetCharacterEncoding.booleanValue()) {
            /*
             * Using a writer or setting the output character encoding means the
             * response will specify a character set. These cases therefore
             * reduce to can the file be read with the correct encoding.
             * (Assuming any BOM is always skipped in the included output.)
             */
            if (targetFile.endsWith("-bom") && useBom.booleanValue() ||
                    targetFile.startsWith(fileEncoding) ||
                    targetFile.equals("cp1252") && fileEncoding.equals("iso-8859-1") ||
                    targetFile.equals("iso-8859-1") && fileEncoding.equals("cp1252")) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
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
            return Boolean.FALSE;
        } else {
            return Boolean.TRUE;
        }
    }


    private final String contextResponseEncoding;
    private final String fileEncoding;
    private final boolean useBom;
    private final String targetFile;
    private final boolean useInclude;
    private final String outputEncoding;
    private final boolean callSetCharacterEncoding;
    private final boolean useWriter;
    private final boolean expectedPass;

    public TestDefaultServletEncoding(String contextResponseEncoding, String fileEncoding,
            boolean useBom, String targetFile, boolean useInclude, String outputEncoding,
            boolean callSetCharacterEncoding, boolean useWriter, boolean expectedPass) {
        this.contextResponseEncoding = contextResponseEncoding;
        this.fileEncoding = fileEncoding;
        this.useBom = useBom;
        this.targetFile = targetFile;
        this.useInclude = useInclude;
        this.outputEncoding = outputEncoding;
        this.callSetCharacterEncoding = callSetCharacterEncoding;
        this.useWriter = useWriter;
        this.expectedPass = expectedPass;
    }

    @Test
    public void testEncoding() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        ctxt.setResponseCharacterEncoding(contextResponseEncoding);
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        defaultServlet.addInitParameter("fileEncoding", fileEncoding);
        defaultServlet.addInitParameter("useBomIfPresent", Boolean.toString(useBom));

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
        List<String> values = headers.get("Content-Type");
        if (values != null && values.size() == 1) {
            MediaType mediaType = MediaType.parseMediaType(new StringReader(values.get(0)));
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
         * Remove BOM before checking content
         * BOM (should be) removed by Tomcat when file is included
         */
        if (!useInclude && targetFile.endsWith("-bom")) {
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

        public EncodingServlet(String outputEncoding, boolean callSetCharacterEncoding,
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
