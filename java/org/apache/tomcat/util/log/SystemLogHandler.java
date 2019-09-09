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
package org.apache.tomcat.util.log;

import java.io.IOException;
import java.io.PrintStream;
import java.util.EmptyStackException;
import java.util.Stack;

/**
 * This helper class may be used to do sophisticated redirection of
 * System.out and System.err on a per Thread basis.
 *
 * A stack is implemented per Thread so that nested startCapture
 * and stopCapture can be used.
 *
 * @author Remy Maucherat
 * @author Glenn L. Nielsen
 */
public class SystemLogHandler extends PrintStream {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct the handler to capture the output of the given steam.
     *
     * @param wrapped The stream to capture
     */
    public SystemLogHandler(PrintStream wrapped) {
        super(wrapped);
        out = wrapped;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Wrapped PrintStream.
     */
    protected PrintStream out = null;


    /**
     * Thread &lt;-&gt; CaptureLog associations.
     */
    protected static ThreadLocal<Stack<CaptureLog>> logs =
        new ThreadLocal<Stack<CaptureLog>>();


    /**
     * Spare CaptureLog ready for reuse.
     */
    protected static Stack<CaptureLog> reuse = new Stack<CaptureLog>();


    // --------------------------------------------------------- Public Methods


    /**
     * Start capturing thread's output.
     */
    public static void startCapture() {
        CaptureLog log = null;
        if (!reuse.isEmpty()) {
            try {
                log = reuse.pop();
            } catch (EmptyStackException e) {
                log = new CaptureLog();
            }
        } else {
            log = new CaptureLog();
        }
        Stack<CaptureLog> stack = logs.get();
        if (stack == null) {
            stack = new Stack<CaptureLog>();
            logs.set(stack);
        }
        stack.push(log);
    }


    /**
     * Stop capturing thread's output.
     *
     * @return The captured data
     */
    public static String stopCapture() {
        Stack<CaptureLog> stack = logs.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CaptureLog log = stack.pop();
        if (log == null) {
            return null;
        }
        String capture = log.getCapture();
        log.reset();
        reuse.push(log);
        return capture;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Find PrintStream to which the output must be written to.
     * @return the print stream
     */
    protected PrintStream findStream() {
        Stack<CaptureLog> stack = logs.get();
        if (stack != null && !stack.isEmpty()) {
            CaptureLog log = stack.peek();
            if (log != null) {
                PrintStream ps = log.getStream();
                if (ps != null) {
                    return ps;
                }
            }
        }
        return out;
    }


    // ---------------------------------------------------- PrintStream Methods


    @Override
    public void flush() {
        findStream().flush();
    }

    @Override
    public void close() {
        findStream().close();
    }

    @Override
    public boolean checkError() {
        return findStream().checkError();
    }

    @Override
    protected void setError() {
        //findStream().setError();
    }

    @Override
    public void write(int b) {
        findStream().write(b);
    }

    @Override
    public void write(byte[] b)
        throws IOException {
        findStream().write(b);
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        findStream().write(buf, off, len);
    }

    @Override
    public void print(boolean b) {
        findStream().print(b);
    }

    @Override
    public void print(char c) {
        findStream().print(c);
    }

    @Override
    public void print(int i) {
        findStream().print(i);
    }

    @Override
    public void print(long l) {
        findStream().print(l);
    }

    @Override
    public void print(float f) {
        findStream().print(f);
    }

    @Override
    public void print(double d) {
        findStream().print(d);
    }

    @Override
    public void print(char[] s) {
        findStream().print(s);
    }

    @Override
    public void print(String s) {
        findStream().print(s);
    }

    @Override
    public void print(Object obj) {
        findStream().print(obj);
    }

    @Override
    public void println() {
        findStream().println();
    }

    @Override
    public void println(boolean x) {
        findStream().println(x);
    }

    @Override
    public void println(char x) {
        findStream().println(x);
    }

    @Override
    public void println(int x) {
        findStream().println(x);
    }

    @Override
    public void println(long x) {
        findStream().println(x);
    }

    @Override
    public void println(float x) {
        findStream().println(x);
    }

    @Override
    public void println(double x) {
        findStream().println(x);
    }

    @Override
    public void println(char[] x) {
        findStream().println(x);
    }

    @Override
    public void println(String x) {
        findStream().println(x);
    }

    @Override
    public void println(Object x) {
        findStream().println(x);
    }

}
