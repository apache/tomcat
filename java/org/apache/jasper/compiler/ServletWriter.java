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
 * This is what is used to generate servlets.
 *
 * @author Anil K. Vijendran
 * @author Kin-man Chung
 */
public class ServletWriter implements AutoCloseable {

    private static final int TAB_WIDTH = 2;
    private static final String SPACES = "                              ";

    /**
     * Current indent level.
     */
    private int indent = 0;
    private int virtual_indent = 0;

    /**
     * The sink writer.
     */
    private final PrintWriter writer;

    /**
     * Servlet line numbers start from 1.
     */
    private int javaLine = 1;


    public ServletWriter(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public void close() {
        writer.close();
    }


    // -------------------- Access information --------------------

    public int getJavaLine() {
        return javaLine;
    }


    // -------------------- Formatting --------------------

    public void pushIndent() {
        virtual_indent += TAB_WIDTH;
        if (virtual_indent >= 0 && virtual_indent <= SPACES.length()) {
            indent = virtual_indent;
        }
    }

    public void popIndent() {
        virtual_indent -= TAB_WIDTH;
        if (virtual_indent >= 0 && virtual_indent <= SPACES.length()) {
            indent = virtual_indent;
        }
    }

    /**
     * Prints the given string followed by '\n'
     * @param s The string
     */
    public void println(String s) {
        javaLine++;
        writer.println(s);
    }

    /**
     * Prints a '\n'
     */
    public void println() {
        javaLine++;
        writer.println("");
    }

    /**
     * Prints the current indentation
     */
    public void printin() {
        writer.print(SPACES.substring(0, indent));
    }

    /**
     * Prints the current indentation, followed by the given string
     * @param s The string
     */
    public void printin(String s) {
        writer.print(SPACES.substring(0, indent));
        writer.print(s);
    }

    /**
     * Prints the current indentation, and then the string, and a '\n'.
     * @param s The string
     */
    public void printil(String s) {
        javaLine++;
        writer.print(SPACES.substring(0, indent));
        writer.println(s);
    }

    /**
     * Prints the given char.
     *
     * Use println() to print a '\n'.
     * @param c The char
     */
    public void print(char c) {
        writer.print(c);
    }

    /**
     * Prints the given int.
     * @param i The int
     */
    public void print(int i) {
        writer.print(i);
    }

    /**
     * Prints the given string.
     *
     * The string must not contain any '\n', otherwise the line count will be
     * off.
     * @param s The string
     */
    public void print(String s) {
        writer.print(s);
    }

    /**
     * Prints the given string.
     *
     * If the string spans multiple lines, the line count will be adjusted
     * accordingly.
     * @param s The string
     */
    public void printMultiLn(String s) {
        int index = 0;

        // look for hidden newlines inside strings
        while ((index=s.indexOf('\n',index)) > -1 ) {
            javaLine++;
            index++;
        }

        writer.print(s);
    }
}
