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
package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.JasperException;
import org.apache.tomcat.unittest.TesterServletContext;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldParser;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;

/**
 * Test cases for {@link TldCache}.
 */
public class TestTldCache {

    private static final String URI_1 = "http://tomcat.apache.org/TesterTld1";
    private static final String URI_2 = "http://tomcat.apache.org/TesterTld2";
    private static final String URI_3 = "http://tomcat.apache.org/TesterTld3";


    private static class TesterTldCacheServletContext extends TesterServletContext {

        private final Map<String,URL> resources = new HashMap<>();

        @Override
        public URL getResource(String path) {
            return resources.get(path);
        }
    }


    private static File createTld(String name, String uri) throws IOException {
        File file = File.createTempFile(name, ".tld");
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<taglib xmlns=\"http://java.sun.com/xml/ns/javaee\""
                + " version=\"2.1\">"
                + "<tlib-version>1.0</tlib-version>"
                + "<short-name>tester</short-name>"
                + "<uri>" + uri + "</uri>"
                + "</taglib>";
        Files.writeString(file.toPath(), content);
        file.deleteOnExit();
        return file;
    }


    /*
     * The cache shares a single, non-thread-safe TldParser across all entries.
     * Re-parsing of different TLDs is only protected by per-entry locks, so it
     * must additionally be serialized on the parser. Before that was fixed,
     * concurrent re-parses of two modified TLDs corrupted the shared digester
     * state and/or swapped the parsed results.
     */
    @Test
    public void testConcurrentReparses() throws Throwable {
        File tldFile1 = createTld("test-tld-cache-1", URI_1);
        File tldFile2 = createTld("test-tld-cache-2", URI_2);

        URL url1 = tldFile1.toURI().toURL();
        URL url2 = tldFile2.toURI().toURL();

        TesterTldCacheServletContext context = new TesterTldCacheServletContext();
        context.resources.put("/tld1.tld", url1);
        context.resources.put("/tld2.tld", url2);

        TldParser parser = new TldParser(true, false, true);
        TldResourcePath path1 = new TldResourcePath(url1, "/tld1.tld");
        TldResourcePath path2 = new TldResourcePath(url2, "/tld2.tld");

        Map<String,TldResourcePath> uriMap = new HashMap<>();
        uriMap.put(URI_1, path1);
        uriMap.put(URI_2, path2);

        Map<TldResourcePath,TaglibXml> xmlMap = new HashMap<>();
        xmlMap.put(path1, parser.parse(path1));
        xmlMap.put(path2, parser.parse(path2));

        TldCache cache = new TldCache(context, uriMap, xmlMap);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);

        Thread reader1 = new Thread(() -> hammer(cache, path1, URI_1, start,
                running, error));
        Thread reader2 = new Thread(() -> hammer(cache, path2, URI_2, start,
                running, error));
        Thread toucher = new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            while (running.get()) {
                // Force a re-parse of both TLDs on every iteration by
                // changing their last modified time
                long now = System.currentTimeMillis();
                tldFile1.setLastModified(now);
                tldFile2.setLastModified(now);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        reader1.start();
        reader2.start();
        toucher.start();
        start.countDown();

        // Let the threads race for a bounded period
        Thread.sleep(1000);
        running.set(false);
        reader1.join();
        reader2.join();
        toucher.join();

        Throwable t = error.get();
        if (t != null) {
            throw t;
        }

        // Final state must still be consistent
        Assert.assertEquals(URI_1, cache.getTaglibXml(path1).getUri());
        Assert.assertEquals(URI_2, cache.getTaglibXml(path2).getUri());
    }


    /*
     * A TLD that was present at startup is cached. If the TLD file is
     * subsequently removed, ServletContext.getResource() returns null for it.
     * getLastModified() previously passed that null on to
     * CloseableURLConnection which threw a NullPointerException.
     */
    @Test
    public void testRemovedTld() throws Exception {
        File tldFile = createTld("test-tld-cache-removed", URI_3);
        URL url = tldFile.toURI().toURL();

        TesterTldCacheServletContext context = new TesterTldCacheServletContext();
        context.resources.put("/removed.tld", url);

        TldParser parser = new TldParser(true, false, true);
        TldResourcePath path = new TldResourcePath(url, "/removed.tld");

        Map<String,TldResourcePath> uriMap = new HashMap<>();
        uriMap.put(URI_3, path);

        Map<TldResourcePath,TaglibXml> xmlMap = new HashMap<>();
        xmlMap.put(path, parser.parse(path));

        TldCache cache = new TldCache(context, uriMap, xmlMap);

        // The cached entry is up to date
        Assert.assertEquals(URI_3, cache.getTaglibXml(path).getUri());

        // Remove the TLD. The context, like a real web application context,
        // no longer finds a resource at the path
        context.resources.remove("/removed.tld");
        Assert.assertTrue(tldFile.delete());

        // The cached entry is out of date and the re-parse of the removed TLD
        // must fail with a JasperException rather than a NullPointerException
        try {
            cache.getTaglibXml(path);
            Assert.fail("Expected a JasperException for the removed TLD");
        } catch (JasperException e) {
            // Expected. The re-parse fails because the TLD can no longer be
            // opened
            Assert.assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof FileNotFoundException);
        }
    }


    private void hammer(TldCache cache, TldResourcePath path, String expectedUri,
            CountDownLatch start, AtomicBoolean running,
            AtomicReference<Throwable> error) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            while (running.get()) {
                TaglibXml taglibXml = cache.getTaglibXml(path);
                if (!expectedUri.equals(taglibXml.getUri())) {
                    throw new IllegalStateException("Unexpected uri [" +
                            taglibXml.getUri() + "] for path [" +
                            path.toExternalForm() + "]");
                }
            }
        } catch (Throwable t) {
            error.compareAndSet(null, t);
        }
    }
}
