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
package org.apache.catalina.ssi;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

public class TestSSIFsize {

    @Test
    public void testFormatSizeIsLocaleIndependent() throws Exception {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Assert.assertEquals("1,234", formatSize(1234, "bytes"));
            Assert.assertEquals(" 1.5M", formatSize(1572864, "abbrev"));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }


    private String formatSize(long size, String format) throws IOException {
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(size), 0);
        mediator.setConfigSizeFmt(format);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        new SSIFsize().process(mediator, "fsize", new String[] { "file" }, new String[] { "any" }, writer);
        writer.flush();
        return stringWriter.toString();
    }

    /**
     * Minimal implementation that provides the bare essentials required for the unit tests.
     */
    private static class TesterSSIExternalResolver implements SSIExternalResolver {

        private final long fileSize;

        TesterSSIExternalResolver(long fileSize) {
            this.fileSize = fileSize;
        }

        @Override
        public void addVariableNames(Collection<String> variableNames) {
            // NO-OP
        }

        @Override
        public String getVariableValue(String name) {
            return null;
        }

        @Override
        public void setVariableValue(String name, String value) {
            // NO-OP
        }

        @Override
        public Date getCurrentDate() {
            return null;
        }

        @Override
        public long getFileSize(String path, boolean virtual) {
            return fileSize;
        }

        @Override
        public long getFileLastModified(String path, boolean virtual) {
            return 0;
        }

        @Override
        public String getFileText(String path, boolean virtual) {
            return null;
        }

        @Override
        public void log(String message, Throwable throwable) {
            // NO-OP
        }
    }
}
