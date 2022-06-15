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

import java.io.PrintWriter;

/**
 * This class filters duplicate newlines instructions from the compiler output,
 * and therefore from the runtime JSP. The duplicates typically happen because
 * the compiler has multiple branches that write them, but they operate
 * independently and don't realize that the previous output was identical.
 *
 * Removing these lines makes the JSP more efficient by executing fewer
 * operations during runtime.
 */
public class NewlineReductionServletWriter extends ServletWriter {

    private static final String NEWLINE_WRITE_TEXT = "out.write('\\n');";

    private boolean lastWriteWasNewline;

    public NewlineReductionServletWriter(PrintWriter writer) {
        super(writer);
    }

    @Override
    public void printil(String s) {
        if (s.equals(NEWLINE_WRITE_TEXT)) {
            if (lastWriteWasNewline) {
                // do nothing
                return;
            } else {
                lastWriteWasNewline = true;
            }
        } else {
            lastWriteWasNewline = false;
        }
        super.printil(s);
    }
}