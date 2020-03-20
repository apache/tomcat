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
package org.apache.tomcat.util.http;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

public class TestConcurrentDateFormat {

    private static final String DATE_RFC5322 = "EEE, dd MMM yyyy HH:mm:ss z";
    TimeZone tz = TimeZone.getTimeZone("GMT");

    @Test
    public void testFormatReturnsGMT() {
        ConcurrentDateFormat format = createConcurrentDateFormat();
        Date date = new Date();
        String formattedDate = format.format(date);
        Assert.assertTrue(formattedDate.endsWith("GMT"));
    }

    @Test
    public void testFormatReturnsGMTAfterParseCET() throws Exception {
        ConcurrentDateFormat format = createConcurrentDateFormat();
        format.parse("Thu, 12 Mar 2020 14:40:22 CET");
        Date date = new Date();
        String formattedDate = format.format(date);
        Assert.assertTrue(formattedDate.endsWith("GMT"));
    }

    private ConcurrentDateFormat createConcurrentDateFormat() {
        return new ConcurrentDateFormat(DATE_RFC5322, Locale.US, tz);
    }
}
