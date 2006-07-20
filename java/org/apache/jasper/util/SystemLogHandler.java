/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jasper.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;


/**
 * This helper class may be used to do sophisticated redirection of 
 * System.out and System.err.
 * 
 * @author Remy Maucherat
 */
public class SystemLogHandler extends PrintStream {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct the handler to capture the output of the given steam.
     */
    public SystemLogHandler(PrintStream wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Wrapped PrintStream.
     */
    protected PrintStream wrapped = null;


    /**
     * Thread <-> PrintStream associations.
     */
    protected static ThreadLocal streams = new ThreadLocal();


    /**
     * Thread <-> ByteArrayOutputStream associations.
     */
    protected static ThreadLocal data = new ThreadLocal();


    // --------------------------------------------------------- Public Methods


    public PrintStream getWrapped() {
      return wrapped;
    }

    /**
     * Start capturing thread's output.
     */
    public static void setThread() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        data.set(baos);
        streams.set(new PrintStream(baos));
    }


    /**
     * Stop capturing thread's output and return captured data as a String.
     */
    public static String unsetThread() {
        ByteArrayOutputStream baos = 
            (ByteArrayOutputStream) data.get();
        if (baos == null) {
            return null;
        }
        streams.set(null);
        data.set(null);
        return baos.toString();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Find PrintStream to which the output must be written to.
     */
    protected PrintStream findStream() {
        PrintStream ps = (PrintStream) streams.get();
        if (ps == null) {
            ps = wrapped;
        }
        return ps;
    }


    // ---------------------------------------------------- PrintStream Methods


    public void flush() {
        findStream().flush();
    }

    public void close() {
        findStream().close();
    }

    public boolean checkError() {
        return findStream().checkError();
    }

    protected void setError() {
        //findStream().setError();
    }

    public void write(int b) {
        findStream().write(b);
    }

    public void write(byte[] b)
        throws IOException {
        findStream().write(b);
    }

    public void write(byte[] buf, int off, int len) {
        findStream().write(buf, off, len);
    }

    public void print(boolean b) {
        findStream().print(b);
    }

    public void print(char c) {
        findStream().print(c);
    }

    public void print(int i) {
        findStream().print(i);
    }

    public void print(long l) {
        findStream().print(l);
    }

    public void print(float f) {
        findStream().print(f);
    }

    public void print(double d) {
        findStream().print(d);
    }

    public void print(char[] s) {
        findStream().print(s);
    }

    public void print(String s) {
        findStream().print(s);
    }

    public void print(Object obj) {
        findStream().print(obj);
    }

    public void println() {
        findStream().println();
    }

    public void println(boolean x) {
        findStream().println(x);
    }

    public void println(char x) {
        findStream().println(x);
    }

    public void println(int x) {
        findStream().println(x);
    }

    public void println(long x) {
        findStream().println(x);
    }

    public void println(float x) {
        findStream().println(x);
    }

    public void println(double x) {
        findStream().println(x);
    }

    public void println(char[] x) {
        findStream().println(x);
    }

    public void println(String x) {
        findStream().println(x);
    }

    public void println(Object x) {
        findStream().println(x);
    }

}
