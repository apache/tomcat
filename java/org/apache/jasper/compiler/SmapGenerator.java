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

/**
 * Represents a source map (SMAP), which serves to associate lines
 * of the input JSP file(s) to lines in the generated servlet in the
 * final .class file, according to the JSR-045 spec.
 *
 * @author Shawn Bayern
 */
public class SmapGenerator {

    //*********************************************************************
    // Overview

    /*
     * The SMAP syntax is reasonably straightforward.  The purpose of this
     * class is currently twofold:
     *  - to provide a simple but low-level Java interface to build
     *    a logical SMAP
     *  - to serialize this logical SMAP for eventual inclusion directly
     *    into a .class file.
     *
     * There are aspects of the SMAP syntax that this class does not support.
     * It provides all the features required for JSP and associated files but no
     * more.
     */


    //*********************************************************************
    // Private state

    private String outputFileName;
    private SmapStratum stratum;

    //*********************************************************************
    // Methods for adding mapping data

    /**
     * Sets the filename (without path information) for the generated
     * source file.  E.g., "foo$jsp.java".
     * @param x The file name
     */
    public synchronized void setOutputFileName(String x) {
        outputFileName = x;
    }


    /**
     * Sets the default and only stratum for the smap.
     *
     * @param stratum the SmapStratum object to add
     */
    public synchronized void setStratum(SmapStratum stratum) {
        this.stratum = stratum;
    }


    //*********************************************************************
    // Methods for serializing the logical SMAP

    public synchronized String getString() {
        // check state and initialize buffer
        if (outputFileName == null) {
            throw new IllegalStateException();
        }

        StringBuilder out = new StringBuilder();

        // start the SMAP
        out.append("SMAP\n");
        out.append(outputFileName + '\n');
        out.append("JSP\n");

        // print our StratumSection, FileSection, and LineSections
        out.append(stratum.getString());

        // end the SMAP
        out.append("*E\n");

        return out.toString();
    }

    @Override
    public String toString() { return getString(); }
}
