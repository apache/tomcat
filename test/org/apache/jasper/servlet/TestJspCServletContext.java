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
package org.apache.jasper.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;

import org.junit.Assert;
import org.junit.Test;

public class TestJspCServletContext {

    @Test
    public void testWebapp() throws Exception {
        File appDir = new File("test/webapp");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(4, context.getEffectiveMajorVersion());
        Assert.assertEquals(0, context.getEffectiveMinorVersion());
        JspConfigDescriptor jspConfigDescriptor =
                context.getJspConfigDescriptor();
        Assert.assertTrue(jspConfigDescriptor.getTaglibs().isEmpty());
        Collection<JspPropertyGroupDescriptor> propertyGroups =
                jspConfigDescriptor.getJspPropertyGroups();
        Assert.assertEquals(4, propertyGroups.size());
        Iterator<JspPropertyGroupDescriptor> groupIterator =
                propertyGroups.iterator();
        JspPropertyGroupDescriptor groupDescriptor;

        groupDescriptor = groupIterator.next();
        Assert.assertEquals("text/plain",
                groupDescriptor.getDefaultContentType());
        Collection<String> urlPatterns =groupDescriptor.getUrlPatterns();
        Assert.assertEquals(2, urlPatterns.size());
        Iterator<String> iterator = urlPatterns.iterator();
        Assert.assertEquals("/bug49nnn/bug49726a.jsp", iterator.next());
        Assert.assertEquals("/bug49nnn/bug49726b.jsp", iterator.next());

        groupDescriptor = groupIterator.next();
        Assert.assertEquals(2, groupDescriptor.getIncludePreludes().size());
        Assert.assertEquals(2, groupDescriptor.getIncludeCodas().size());
    }

    @Test
    public void testWebapp_2_2() throws Exception {
        File appDir = new File("test/webapp-2.2");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(2, context.getEffectiveMajorVersion());
        Assert.assertEquals(2, context.getEffectiveMinorVersion());
    }

    @Test
    public void testWebapp_2_3() throws Exception {
        File appDir = new File("test/webapp-2.3");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(2, context.getEffectiveMajorVersion());
        Assert.assertEquals(3, context.getEffectiveMinorVersion());
    }

    @Test
    public void testWebapp_2_4() throws Exception {
        File appDir = new File("test/webapp-2.4");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(2, context.getEffectiveMajorVersion());
        Assert.assertEquals(4, context.getEffectiveMinorVersion());
    }

    @Test
    public void testWebapp_2_5() throws Exception {
        File appDir = new File("test/webapp-2.5");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(2, context.getEffectiveMajorVersion());
        Assert.assertEquals(5, context.getEffectiveMinorVersion());
    }

    @Test
    public void testWebapp_3_0() throws Exception {
        File appDir = new File("test/webapp-3.0");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(3, context.getEffectiveMajorVersion());
        Assert.assertEquals(0, context.getEffectiveMinorVersion());
    }

    @Test
    public void testWebapp_3_1() throws Exception {
        File appDir = new File("test/webapp-3.1");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(3, context.getEffectiveMajorVersion());
        Assert.assertEquals(1, context.getEffectiveMinorVersion());
    }

    @Test
    public void testWebapp_4_0() throws Exception {
        File appDir = new File("test/webapp-4.0");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(4, context.getEffectiveMajorVersion());
        Assert.assertEquals(0, context.getEffectiveMinorVersion());
    }


    @Test
    public void testWebresources() throws Exception {
        File appDir = new File("test/webresources/dir1");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);
        Assert.assertEquals(4, context.getEffectiveMajorVersion());
        Assert.assertEquals(0, context.getEffectiveMinorVersion());
    }


    @Test
    public void testGetResourceWithUriIllegalCharacters() throws Exception {
        // A resource path is a decoded logical path. Resources whose name
        // contains characters that are not permitted in a URI, for example a
        // space or a non-ASCII character, resolve correctly at runtime so
        // they must also resolve for the offline compiler rather than be
        // silently reported as not found.
        File docBase = Files.createTempDirectory("jspc-illegal-chars").toFile();
        try {
            try (FileWriter writer = new FileWriter(new File(docBase, "my file.jsp"))) {
                writer.write("<jsp/>");
            }
            try (FileWriter writer = new FileWriter(new File(docBase, "unicode-\u00fc.jsp"))) {
                writer.write("<jsp/>");
            }
            JspCServletContext context = new JspCServletContext(
                    null, docBase.toURI().toURL(), null, false, false);

            URL url = context.getResource("/my file.jsp");
            Assert.assertNotNull(url);
            try (InputStream is = url.openStream()) {
                Assert.assertEquals("<jsp/>",
                        new String(readFully(is), StandardCharsets.UTF_8));
            }
            Assert.assertNotNull(context.getResource("/unicode-\u00fc.jsp"));
        } finally {
            deleteRecursively(docBase);
        }
    }

    private static byte[] readFully(InputStream is) throws IOException {
        byte[] buf = new byte[1024];
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int read;
            while ((read = is.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        Assert.assertTrue(file.delete());
    }


    @Test
    public void testResourceJARs() throws Exception {
        File appDir = new File("test/webapp-fragments");
        JspCServletContext context = new JspCServletContext(
                null, appDir.toURI().toURL(), null, false, false);

        Set<String> paths = context.getResourcePaths("/");
        Assert.assertEquals(10, paths.size());
        Assert.assertTrue(paths.contains("/WEB-INF/"));
        Assert.assertTrue(paths.contains("/folder/"));
        Assert.assertTrue(paths.contains("/'singlequote.jsp"));
        Assert.assertTrue(paths.contains("/'singlequote2.jsp"));
        Assert.assertTrue(paths.contains("/bug51396.jsp"));
        Assert.assertTrue(paths.contains("/jndi.jsp"));
        Assert.assertTrue(paths.contains("/resourceA.jsp"));
        Assert.assertTrue(paths.contains("/resourceB.jsp"));
        Assert.assertTrue(paths.contains("/resourceF.jsp"));
        Assert.assertTrue(paths.contains("/warDirContext.jsp"));

        paths = context.getResourcePaths("/folder/");
        Assert.assertEquals(3, paths.size());
        Assert.assertTrue(paths.contains("/folder/resourceC.jsp"));
        Assert.assertTrue(paths.contains("/folder/resourceD.jsp"));
        Assert.assertTrue(paths.contains("/folder/resourceE.jsp"));
    }
}
