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
package org.apache.tomcat.util.descriptor.tld;

import java.io.File;
import java.io.IOException;
import java.util.List;

import jakarta.servlet.jsp.tagext.FunctionInfo;
import jakarta.servlet.jsp.tagext.TagAttributeInfo;
import jakarta.servlet.jsp.tagext.TagVariableInfo;
import jakarta.servlet.jsp.tagext.VariableInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xml.sax.SAXException;

public class TestTldParser {
    private TldParser parser;

    @Before
    public void init() {
        parser = new TldParser(true, true, new TldRuleSet(), true);
    }

    @Test
    public void testTld() throws Exception {
        TaglibXml xml = parse("test/tld/test.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        Assert.assertEquals("2.1", xml.getJspVersion());
        Assert.assertEquals("test", xml.getShortName());
        Assert.assertEquals("http://tomcat.apache.org/TldTests", xml.getUri());
        Assert.assertEquals(1, xml.getFunctions().size());

        ValidatorXml validator = xml.getValidator();
        Assert.assertEquals("com.example.Validator", validator.getValidatorClass());
        Assert.assertEquals(1, validator.getInitParams().size());
        Assert.assertEquals("value", validator.getInitParams().get("name"));

        Assert.assertEquals(1, xml.getTags().size());
        TagXml tag = xml.getTags().get(0);
        Assert.assertEquals("org.apache.jasper.compiler.TestValidator$Echo", tag.getTagClass());
        Assert.assertEquals("empty", tag.getBodyContent());
        Assert.assertTrue(tag.hasDynamicAttributes());

        Assert.assertEquals(1, tag.getVariables().size());
        TagVariableInfo variableInfo = tag.getVariables().get(0);
        Assert.assertEquals("var", variableInfo.getNameGiven());
        Assert.assertEquals("java.lang.Object", variableInfo.getClassName());
        Assert.assertTrue(variableInfo.getDeclare());
        Assert.assertEquals(VariableInfo.AT_END, variableInfo.getScope());

        Assert.assertEquals(4, tag.getAttributes().size());
        TagAttributeInfo attributeInfo = tag.getAttributes().get(0);
        Assert.assertEquals("Echo Tag", tag.getInfo());
        Assert.assertEquals("Echo", tag.getDisplayName());
        Assert.assertEquals("small", tag.getSmallIcon());
        Assert.assertEquals("large", tag.getLargeIcon());
        Assert.assertEquals("echo", attributeInfo.getName());
        Assert.assertTrue(attributeInfo.isRequired());
        Assert.assertTrue(attributeInfo.canBeRequestTime());

        attributeInfo = tag.getAttributes().get(1);
        Assert.assertEquals("fragment", attributeInfo.getName());
        Assert.assertTrue(attributeInfo.isFragment());
        Assert.assertTrue(attributeInfo.canBeRequestTime());
        Assert.assertEquals("jakarta.servlet.jsp.tagext.JspFragment", attributeInfo.getTypeName());

        attributeInfo = tag.getAttributes().get(2);
        Assert.assertEquals("deferredValue", attributeInfo.getName());
        Assert.assertEquals("jakarta.el.ValueExpression", attributeInfo.getTypeName());
        Assert.assertEquals("java.util.Date", attributeInfo.getExpectedTypeName());

        attributeInfo = tag.getAttributes().get(3);
        Assert.assertEquals("deferredMethod", attributeInfo.getName());
        Assert.assertEquals("jakarta.el.MethodExpression", attributeInfo.getTypeName());
        Assert.assertEquals("java.util.Date getDate()", attributeInfo.getMethodSignature());

        Assert.assertEquals(1, xml.getTagFiles().size());
        TagFileXml tagFile = xml.getTagFiles().get(0);
        Assert.assertEquals("Echo", tag.getDisplayName());
        Assert.assertEquals("small", tag.getSmallIcon());
        Assert.assertEquals("large", tag.getLargeIcon());
        Assert.assertEquals("Echo2", tagFile.getName());
        Assert.assertEquals("/echo.tag", tagFile.getPath());

        Assert.assertEquals(1, xml.getFunctions().size());
        FunctionInfo fn = xml.getFunctions().get(0);
        Assert.assertEquals("trim", fn.getName());
        Assert.assertEquals("org.apache.el.TesterFunctions", fn.getFunctionClass());
        Assert.assertEquals("java.lang.String trim(java.lang.String)", fn.getFunctionSignature());
    }

    @Test
    public void testParseTld21() throws Exception {
        TaglibXml xml = parse("test/tld/tags21.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        Assert.assertEquals("2.1", xml.getJspVersion());
        Assert.assertEquals("Tags21", xml.getShortName());
        Assert.assertEquals("http://tomcat.apache.org/tags21", xml.getUri());
        verifyTags(xml.getTags());
    }

    @Test
    public void testParseTld20() throws Exception {
        TaglibXml xml = parse("test/tld/tags20.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        Assert.assertEquals("2.0", xml.getJspVersion());
        Assert.assertEquals("Tags20", xml.getShortName());
        Assert.assertEquals("http://tomcat.apache.org/tags20", xml.getUri());
        verifyTags(xml.getTags());
    }

    @Test
    public void testParseTld12() throws Exception {
        TaglibXml xml = parse("test/tld/tags12.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        Assert.assertEquals("1.2", xml.getJspVersion());
        Assert.assertEquals("Tags12", xml.getShortName());
        Assert.assertEquals("http://tomcat.apache.org/tags12", xml.getUri());
        verifyTags(xml.getTags());
    }

    @Test
    public void testParseTld11() throws Exception {
        TaglibXml xml = parse("test/tld/tags11.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        Assert.assertEquals("1.1", xml.getJspVersion());
        Assert.assertEquals("Tags11", xml.getShortName());
        Assert.assertEquals("http://tomcat.apache.org/tags11", xml.getUri());
        verifyTags(xml.getTags());
    }

    private void verifyTags(List<TagXml> tags) {
        Assert.assertEquals(1, tags.size());
        TagXml tag = tags.get(0);
        Assert.assertEquals("Echo", tag.getName());
        Assert.assertEquals("org.apache.jasper.compiler.TestValidator$Echo", tag.getTagClass());
        Assert.assertEquals("empty", tag.getBodyContent());
    }

    @Test
    public void testListener() throws Exception {
        TaglibXml xml = parse("test/tld/listener.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        List<String> listeners = xml.getListeners();
        Assert.assertEquals(1, listeners.size());
        Assert.assertEquals("org.apache.catalina.core.TesterTldListener", listeners.get(0));
    }

    private TaglibXml parse(String pathname) throws IOException, SAXException {
        File file = new File(pathname);
        TldResourcePath path = new TldResourcePath(file.toURI().toURL(), null);
        return parser.parse(path);
    }

}
