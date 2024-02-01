/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.filters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.filters.CsrfPreventionFilter.LruCache;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterServletContext;

public class TestCsrfPreventionFilter extends TomcatBaseTest {

    private static final String RESULT_NONCE = Constants.CSRF_NONCE_SESSION_ATTR_NAME + "=TESTNONCE";

    private final HttpServletResponse wrapper = new CsrfPreventionFilter.CsrfResponseWrapper(new NonEncodingResponse(),
            Constants.CSRF_NONCE_SESSION_ATTR_NAME, "TESTNONCE", null);

    @Test
    public void testAddNonceNoQueryNoAnchor() throws Exception {
        Assert.assertEquals("/test?" + RESULT_NONCE, wrapper.encodeRedirectURL("/test"));
    }

    @Test
    public void testAddNonceQueryNoAnchor() throws Exception {
        Assert.assertEquals("/test?a=b&" + RESULT_NONCE, wrapper.encodeRedirectURL("/test?a=b"));
    }

    @Test
    public void testAddNonceNoQueryAnchor() throws Exception {
        Assert.assertEquals("/test?" + RESULT_NONCE + "#c", wrapper.encodeRedirectURL("/test#c"));
    }

    @Test
    public void testAddNonceQueryAnchor() throws Exception {
        Assert.assertEquals("/test?a=b&" + RESULT_NONCE + "#c", wrapper.encodeRedirectURL("/test?a=b#c"));
    }

    @Test
    public void testLruCacheSerializable() throws Exception {
        LruCache<String> cache = new LruCache<>(5);
        cache.add("key1");
        cache.add("key2");
        cache.add("key3");
        cache.add("key4");
        cache.add("key5");
        cache.add("key6");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(cache);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        @SuppressWarnings("unchecked")
        LruCache<String> cache2 = (LruCache<String>) ois.readObject();

        cache2.add("key7");
        Assert.assertFalse(cache2.contains("key1"));
        Assert.assertFalse(cache2.contains("key2"));
        Assert.assertTrue(cache2.contains("key3"));
        Assert.assertTrue(cache2.contains("key4"));
        Assert.assertTrue(cache2.contains("key5"));
        Assert.assertTrue(cache2.contains("key6"));
        Assert.assertTrue(cache2.contains("key7"));
    }

    @Test
    public void testLruCacheSerializablePerformance() throws Exception {
        for (int i = 0; i < 10000; i++) {
            testLruCacheSerializable();
        }
    }


    @Test
    public void testNoNonceBuilders() {
        Assert.assertEquals(CsrfPreventionFilter.PrefixPredicate.class, CsrfPreventionFilter.createNoNoncePredicate(null, "/images/*").getClass());
        Assert.assertEquals(CsrfPreventionFilter.SuffixPredicate.class, CsrfPreventionFilter.createNoNoncePredicate(null, "*.png").getClass());
        Assert.assertEquals(CsrfPreventionFilter.PatternPredicate.class, CsrfPreventionFilter.createNoNoncePredicate(null, "/^(/images/.*|.*\\.png)$/").getClass());

        Collection<Predicate<String>> chain = CsrfPreventionFilter.createNoNoncePredicates(null, "*.png,/js/*,*.jpg,/images/*,mime:*/png,mime:image/*");

        Assert.assertEquals(6, chain.size());
        Iterator<Predicate<String>> items = chain.iterator();

        Assert.assertEquals(CsrfPreventionFilter.SuffixPredicate.class, items.next().getClass());
        Assert.assertEquals(CsrfPreventionFilter.PrefixPredicate.class, items.next().getClass());
        Assert.assertEquals(CsrfPreventionFilter.SuffixPredicate.class, items.next().getClass());
        Assert.assertEquals(CsrfPreventionFilter.PrefixPredicate.class, items.next().getClass());
        Predicate<String> item = items.next();
        Assert.assertEquals(CsrfPreventionFilter.MimePredicate.class, item.getClass());
        Assert.assertEquals(CsrfPreventionFilter.SuffixPredicate.class, ((CsrfPreventionFilter.MimePredicate)item).getPredicate().getClass());

        item = items.next();
        Assert.assertEquals(CsrfPreventionFilter.MimePredicate.class, item.getClass());
        Assert.assertEquals(CsrfPreventionFilter.PrefixPredicate.class, ((CsrfPreventionFilter.MimePredicate)item).getPredicate().getClass());
    }

    @Test
    public void testNoNoncePatternMatchers() {
        String[] urls = { "/images/home.png" };
        Predicate<String> prefix = new CsrfPreventionFilter.PrefixPredicate("/images/");
        Predicate<String> suffix = new CsrfPreventionFilter.SuffixPredicate(".png");
        Predicate<String> regex = new CsrfPreventionFilter.PatternPredicate("^(/images/.*|.*\\.png)$");

        for(String url : urls) {
            Assert.assertTrue("Prefix match fails", prefix.test(url));
            Assert.assertTrue("Suffix match fails", suffix.test(url));
            Assert.assertTrue("Pattern match fails", regex.test(url));
        }

        ArrayList<Predicate<String>> chain = new ArrayList<>();
        chain.add(prefix);
        chain.add(suffix);
        chain.add(regex);

        HttpServletResponse response = new CsrfPreventionFilter.CsrfResponseWrapper(new NonEncodingResponse(),
                Constants.CSRF_NONCE_SESSION_ATTR_NAME, "TESTNONCE", chain);

        // These URLs should include nonces
        Assert.assertEquals("/foo?" + RESULT_NONCE, response.encodeURL("/foo"));
        Assert.assertEquals("/foo/images?" + RESULT_NONCE, response.encodeURL("/foo/images"));
        Assert.assertEquals("/foo/images/home.jpg?" + RESULT_NONCE, response.encodeURL("/foo/images/home.jpg"));

        // These URLs should not
        Assert.assertEquals("/images/home.png", response.encodeURL("/images/home.png"));
        Assert.assertEquals("/images/home.jpg", response.encodeURL("/images/home.jpg"));
        Assert.assertEquals("/home.png", response.encodeURL("/home.png"));
        Assert.assertEquals("/home.png", response.encodeURL("/home.png"));
    }

    @Test
    public void testNoNonceMimeMatcher() {
        MimeTypeServletContext context = new MimeTypeServletContext();
        Predicate<String> mime = new CsrfPreventionFilter.MimePredicate(context, new CsrfPreventionFilter.PrefixPredicate("image/"));

        context.setMimeType("image/png");
        Assert.assertTrue("MIME match fails", mime.test("/images/home.png"));

        context.setMimeType("text/plain");
        Assert.assertFalse("MIME match succeeds where it should fail", mime.test("/test.txt"));

        Collection<Predicate<String>> chain = Collections.singleton(mime);
        HttpServletResponse response = new CsrfPreventionFilter.CsrfResponseWrapper(new NonEncodingResponse(),
                Constants.CSRF_NONCE_SESSION_ATTR_NAME, "TESTNONCE", chain);

        // These URLs should include nonces
        Assert.assertEquals("/foo?" + RESULT_NONCE, response.encodeURL("/foo"));
        Assert.assertEquals("/foo/images?" + RESULT_NONCE, response.encodeURL("/foo/images"));
        Assert.assertEquals("/foo/images/home.jpg?" + RESULT_NONCE, response.encodeURL("/foo/images/home.jpg"));
        Assert.assertEquals("/images/home.png?" + RESULT_NONCE, response.encodeURL("/images/home.png"));
        Assert.assertEquals("/images/home.jpg?" + RESULT_NONCE, response.encodeURL("/images/home.jpg"));
        Assert.assertEquals("/home.png?" + RESULT_NONCE, response.encodeURL("/home.png"));

        context.setMimeType("image/png");
        // These URLs should not
        Assert.assertEquals("/images/home.png", response.encodeURL("/images/home.png"));
        Assert.assertEquals("/images/home.jpg", response.encodeURL("/images/home.jpg"));
        Assert.assertEquals("/home.png", response.encodeURL("/home.png"));
        Assert.assertEquals("/foo", response.encodeURL("/foo"));
        Assert.assertEquals("/foo/home.png", response.encodeURL("/foo/home.png"));
        Assert.assertEquals("/foo/images/home.jpg", response.encodeURL("/foo/images/home.jpg"));
    }

    private static class MimeTypeServletContext extends TesterServletContext {
        private String mimeType;
        public void setMimeType(String type) {
            mimeType = type;
        }

        @Override
        public String getMimeType(String url) {
            return mimeType;
        }
    }
    private static class NonEncodingResponse extends TesterHttpServletResponse {

        @Override
        public String encodeRedirectURL(String url) {
            return url;
        }

        @Override
        public String encodeURL(String url) {
            return url;
        }
    }
}
