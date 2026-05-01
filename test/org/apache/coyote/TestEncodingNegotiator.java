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
package org.apache.coyote;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.coyote.http11.OutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilterFactory;
import org.apache.coyote.http11.filters.OutputFilterFactory;
import org.apache.tomcat.util.http.parser.AcceptEncoding;
import org.apache.tomcat.util.http.parser.TE;
import org.junit.Assert;
import org.junit.Test;

public class TestEncodingNegotiator {

    private static OutputFilterFactory deflateFactory() {
        return new OutputFilterFactory() {
            @Override
            public OutputFilter createFilter() {
                return new GzipOutputFilter();
            }

            @Override
            public String getEncodingName() {
                return "deflate";
            }
        };
    }

    private static OutputFilterFactory brFactory() {
        return new OutputFilterFactory() {
            @Override
            public OutputFilter createFilter() {
                return new GzipOutputFilter();
            }

            @Override
            public String getEncodingName() {
                return "br";
            }
        };
    }

    @Test
    public void testAcceptEncodingPrefersHigherQ() throws Exception {
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory()); // server priority 0
        factories.add(deflateFactory());              // server priority 1

        List<AcceptEncoding> aes = AcceptEncoding.parse(new StringReader("gzip;q=0.5, deflate;q=1.0"));

        OutputFilterFactory selected = EncodingNegotiator.negotiateAcceptEncoding(factories, aes);
        Assert.assertNotNull(selected);
        Assert.assertEquals("deflate", selected.getEncodingName());
    }

    @Test
    public void testAcceptEncodingWildcardServerPriority() throws Exception {
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(brFactory());
        factories.add(new GzipOutputFilterFactory());

        List<AcceptEncoding> aes = AcceptEncoding.parse(new StringReader("*"));

        OutputFilterFactory selected = EncodingNegotiator.negotiateAcceptEncoding(factories, aes);
        Assert.assertNotNull(selected);
        Assert.assertEquals("br", selected.getEncodingName());
    }

    @Test
    public void testAcceptEncodingTieServerPriority() throws Exception {
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory()); // index 0
        factories.add(deflateFactory());              // index 1

        List<AcceptEncoding> aes = AcceptEncoding.parse(new StringReader("gzip, deflate"));

        OutputFilterFactory selected = EncodingNegotiator.negotiateAcceptEncoding(factories, aes);
        Assert.assertNotNull(selected);
        Assert.assertEquals("gzip", selected.getEncodingName());
    }

    @Test
    public void testAcceptEncodingQZeroExcludes() throws Exception {
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory());
        factories.add(deflateFactory());

        List<AcceptEncoding> aes = AcceptEncoding.parse(new StringReader("gzip;q=0, deflate;q=1"));

        OutputFilterFactory selected = EncodingNegotiator.negotiateAcceptEncoding(factories, aes);
        Assert.assertNotNull(selected);
        Assert.assertEquals("deflate", selected.getEncodingName());
    }

    @Test
    public void testTENegotiationPrefersHigherQ() throws Exception {
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory());
        factories.add(deflateFactory());

        List<TE> tes = TE.parse(new StringReader("deflate;q=1.0, gzip;q=0.5"));

        OutputFilterFactory selected = EncodingNegotiator.negotiateTE(factories, tes);
        Assert.assertNotNull(selected);
        Assert.assertEquals("deflate", selected.getEncodingName());
    }

    @Test
    public void testTENegotiationWildcardServerPriority() throws Exception {
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(brFactory());
        factories.add(new GzipOutputFilterFactory());

        List<TE> tes = TE.parse(new StringReader("*"));

        OutputFilterFactory selected = EncodingNegotiator.negotiateTE(factories, tes);
        Assert.assertNotNull(selected);
        Assert.assertEquals("br", selected.getEncodingName());
    }
}

