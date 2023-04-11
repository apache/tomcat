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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.Reader;

import org.apache.tomcat.util.http.parser.StructuredField.SfBoolean;
import org.apache.tomcat.util.http.parser.StructuredField.SfDictionary;
import org.apache.tomcat.util.http.parser.StructuredField.SfInteger;
import org.apache.tomcat.util.http.parser.StructuredField.SfListMember;

/**
 * HTTP priority header parser as per RFC 9218.
 */
public class Priority {

    public static final int DEFAULT_URGENCY = 3;
    public static final boolean DEFAULT_INCREMENTAL = false;

    // Explicitly set the defaults as per RFC 9218
    private int urgency = DEFAULT_URGENCY;
    private boolean incremental = DEFAULT_INCREMENTAL;

    public Priority() {
        // Default constructor is NO-OP.
    }

    public int getUrgency() {
        return urgency;
    }

    public void setUrgency(int urgency) {
        this.urgency = urgency;
    }

    public boolean getIncremental() {
        return incremental;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }


    /**
     * Parsers an HTTP header as a Priority header as defined by RFC 9218.
     *
     * @param input The header to parse
     *
     * @return The resulting priority
     *
     * @throws IOException If an I/O error occurs while reading the input
     */
    public static Priority parsePriority(Reader input) throws IOException {
        Priority result = new Priority();

        SfDictionary dictionary = StructuredField.parseSfDictionary(input);

        SfListMember urgencyListMember = dictionary.getDictionaryMember("u");
        // If not an integer, ignore it
        if (urgencyListMember instanceof SfInteger) {
            long urgency = ((SfInteger) urgencyListMember).getVaue().longValue();
            // If out of range, ignore it
            if (urgency > -1 && urgency < 8) {
                result.setUrgency((int) urgency);
            }
        }

        SfListMember incrementalListMember = dictionary.getDictionaryMember("i");
        // If not a boolean, ignore it
        if (incrementalListMember instanceof SfBoolean) {
            result.setIncremental(((SfBoolean) incrementalListMember).getVaue().booleanValue());
        }

        return result;
    }
}
