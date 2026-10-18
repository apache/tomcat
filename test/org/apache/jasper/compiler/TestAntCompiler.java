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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.easymock.EasyMock;

public class TestAntCompiler {

    @After
    public void clearCapture() {
        // Never leave the System.err capture installed on the test thread,
        // even if an assertion below fails
        AntCompiler.SystemLogHandler.unsetThread();
    }

    /*
     * A compilation that fails unexpectedly, with the exception escaping
     * generateClass(), must not leave the per-thread System.err capture
     * installed. Otherwise, later output to System.err on the (pooled) thread
     * would be silently discarded for the life of that thread.
     */
    @Test
    public void testErrorCaptureClearedWhenCompilationFails() throws Exception {
        Options options = EasyMock.createMock(Options.class);
        EasyMock.expect(options.getJavaEncoding()).andReturn("UTF-8").anyTimes();
        EasyMock.expect(options.getCompiler()).andReturn(null).anyTimes();
        EasyMock.replay(options);

        JspCompilationContext ctxt = EasyMock.createMock(
                JspCompilationContext.class);
        EasyMock.expect(ctxt.getOptions()).andReturn(options).anyTimes();
        EasyMock.expect(ctxt.getServletJavaFileName())
                .andReturn("none_jsp.java").anyTimes();
        // A null class path triggers an unexpected exception while the
        // javac task is being configured, after the capture has started
        EasyMock.expect(ctxt.getClassPath()).andReturn(null).anyTimes();
        EasyMock.replay(ctxt);

        AntCompiler compiler = new AntCompiler();
        compiler.init(ctxt, null);

        try {
            compiler.generateClass(null);
            Assert.fail("Compilation was expected to fail");
        } catch (Exception expected) {
            // Ignore
        }

        // The capture must not be active on this thread any more
        Assert.assertNull(AntCompiler.SystemLogHandler.streams.get());
        Assert.assertNull(AntCompiler.SystemLogHandler.data.get());
        Assert.assertNull(AntCompiler.SystemLogHandler.unsetThread());

        EasyMock.verify(options, ctxt);
    }
}
