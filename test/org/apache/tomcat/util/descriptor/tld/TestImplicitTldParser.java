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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xml.sax.SAXException;

public class TestImplicitTldParser {
    private TldParser parser;

    @Before
    public void init() {
        parser = new TldParser(true, true, new ImplicitTldRuleSet(), true);
    }

    @Test
    public void testImpicitTldGood() throws Exception {
        TaglibXml xml = parse("test/tld/implicit-good.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        Assert.assertEquals("2.1", xml.getJspVersion());
        Assert.assertEquals("Ignored", xml.getShortName());
    }

    @Test
    public void testImpicitTldBad() throws Exception {
        TaglibXml xml = parse("test/tld/implicit-bad.tld");
        Assert.assertEquals("1.0", xml.getTlibVersion());
        Assert.assertEquals("2.1", xml.getJspVersion());
        Assert.assertEquals("Ignored", xml.getShortName());
    }

    private TaglibXml parse(String pathname) throws IOException, SAXException {
        File file = new File(pathname);
        TldResourcePath path = new TldResourcePath(file.toURI().toURL(), null);
        return parser.parse(path);
    }

}
