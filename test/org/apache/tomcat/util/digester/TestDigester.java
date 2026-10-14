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
package org.apache.tomcat.util.digester;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class TestDigester {

    /*
     * A package level view of the internal stacks so that the state left after
     * a failed parse can be inspected without using reflection.
     */
    private static final class InspectableDigester extends Digester {

        int getMatchLength() {
            return match.length();
        }

        int getMatchesSize() {
            return matches.size();
        }

        int getNamespacesSize() {
            return namespaces.size();
        }

        int getParamsSize() {
            return params.size();
        }
    }


    private static final class CountingRule extends Rule {

        private int begins;

        @Override
        public void begin(String namespace, String name, Attributes attributes) throws SAXException {
            begins++;
        }

        int getBegins() {
            return begins;
        }
    }


    /*
     * Ensures the failed parse throws regardless of whether the parser itself
     * re-throws fatal errors that the error handler accepts.
     */
    private static void rethrowFatalErrors(Digester digester) {
        digester.setErrorHandler(new DefaultHandler() {
            @Override
            public void fatalError(SAXParseException exception) throws SAXParseException {
                throw exception;
            }
        });
    }


    /*
     * A fatal parse error aborts the parse before endDocument() (and with it
     * clear()) runs, so the state must be reset explicitly to leave the
     * Digester reusable. ContextConfig parses up to three context.xml files
     * with a single shared Digester instance.
     */
    @Test
    public void testParseFailureClearsState() throws Exception {
        InspectableDigester digester = new InspectableDigester();
        digester.setNamespaceAware(true);
        rethrowFatalErrors(digester);
        digester.addObjectCreate("root", "java.lang.Object");
        digester.addCallMethod("root/method", "setString", 1);

        try {
            // The end tag of root is encountered while method is still open.
            // The namespace prefix mapping is not deregistered either.
            digester.parse(new InputSource(
                    new StringReader("<root xmlns:p='urn:test:p'><method></root>")));
            Assert.fail("Expected a SAXException for the mismatched end tag");
        } catch (SAXException e) {
            // Expected
        }

        Assert.assertNull(digester.getRoot());
        Assert.assertEquals(0, digester.getCount());
        Assert.assertEquals(0, digester.getMatchLength());
        Assert.assertEquals(0, digester.getMatchesSize());
        Assert.assertEquals(0, digester.getNamespacesSize());
        Assert.assertEquals(0, digester.getParamsSize());
    }


    /*
     * The match path left by a failed parse must not be carried over to the
     * next parse on the same instance, otherwise rules no longer match at the
     * path they were registered for.
     */
    @Test
    public void testRulesMatchAfterFailedParse() throws Exception {
        Digester digester = new Digester();
        rethrowFatalErrors(digester);
        CountingRule rule = new CountingRule();
        digester.addRule("root/child", rule);

        try {
            // The end tag of root is encountered while bad is still open. The
            // rule above does not fire for the failed parse.
            digester.parse(new InputSource(new StringReader("<root><bad></root>")));
            Assert.fail("Expected a SAXException for the mismatched end tag");
        } catch (SAXException e) {
            // Expected
        }

        digester.parse(new InputSource(new StringReader("<root><child/></root>")));

        Assert.assertEquals(1, rule.getBegins());
    }


    /*
     * clear() runs at the end of every successful parse. The methods that are
     * documented to return null on an empty stack must keep working after the
     * digester has been cleared.
     */
    @Test
    public void testStackMethodsAfterSuccessfulParse() throws Exception {
        Digester digester = new Digester();
        digester.addObjectCreate("root", "java.lang.Object");

        digester.parse(new InputSource(new StringReader("<root/>")));

        Assert.assertNull(digester.peek());
        Assert.assertNull(digester.peek(0));
        Assert.assertNull(digester.pop());
        Assert.assertNull(digester.peekParams());
        Assert.assertNull(digester.popParams());
        digester.pushParams(new Object());
        digester.register("public-id", "system-id");
    }


    /*
     * A parse failure before setDocumentLocator() leaves the locator null, so
     * createSAXException() takes the log.error path. That path must work even
     * though the failed parse has run clear() via the parse() failure handling.
     */
    @Test
    public void testCreateSaxExceptionAfterFailedParseWithoutLocator() throws Exception {
        Digester digester = new Digester();

        try {
            digester.parse(new File("does-not-exist.xml"));
            Assert.fail("Expected an IOException for the missing file");
        } catch (IOException e) {
            // Expected
        }

        Assert.assertNotNull(digester.createSAXException("message", new RuntimeException("cause")));
    }


    /*
     * peek(int) is documented to return null for any out of range index,
     * including negative ones such as the values a negative CallMethodRule
     * target offset can produce.
     */
    @Test
    public void testPeekOutOfRangeIndexReturnsNull() {
        Digester digester = new Digester();
        digester.push(new Object());

        Assert.assertNotNull(digester.peek(0));
        Assert.assertNull(digester.peek(1));
        Assert.assertNull(digester.peek(-1));
    }

}
