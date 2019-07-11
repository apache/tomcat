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
package javax.servlet.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.ErrorPage;

/**
 * These tests evolved out of a discussion in the Jakarta Servlet project
 * regarding the intended behaviour in various error scenarios. Async requests
 * and/or async error pages added additional complexity.
 */
@RunWith(Parameterized.class)
public class TestHttpServletResponseSendError extends TomcatBaseTest {

    /*
     * Implementation notes:
     * Original Request
     *   - async
     *     - error in original thread / new thread
     *     - error before / after startAsync
     *     - error before / after complete / dispatch
     * Error page
     *   - sync
     *   - async
     *     - complete
     *     - dispatch
     */

    private enum AsyncErrorPoint {
        /*
         * Thread A is the container thread the processes the original request.
         * Thread B is the async thread (may or may not be a container thread)
         *   that is started by the async processing.
         */
        THREAD_A_BEFORE_START_ASYNC,
        THREAD_A_AFTER_START_ASYNC,
        THREAD_A_AFTER_START_RUNNABLE,
        THREAD_B_BEFORE_COMPLETE
        /*
         * If the error is triggered after Thread B completes async processing
         * there is essentially a race condition between thread B making the
         * change and the container checking to see if the error flag has been
         * set. We can't easily control the execution order here so we don't
         * test it.
         */
    }


    @Parameterized.Parameters(name = "{index}: async[{0}], throw[{1}], dispatch[{2}], errorPoint[{3}], useStart[{4}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (Boolean async : booleans) {
            for (Boolean throwException : booleans) {
                if (async.booleanValue()) {
                    for (Boolean useDispatch : booleans) {
                        for (AsyncErrorPoint errorPoint : AsyncErrorPoint.values()) {
                            for (Boolean useStart : booleans) {
                                if (throwException.booleanValue() && !useStart.booleanValue() &&
                                        errorPoint == AsyncErrorPoint.THREAD_B_BEFORE_COMPLETE) {
                                    // Skip this combination as exceptions that occur on application
                                    // managed threads are not visible to the container.
                                    continue;
                                }
                                parameterSets.add(new Object[] { async, throwException, useDispatch,
                                        errorPoint, useStart} );
                            }
                        }
                    }
                } else {
                    // Ignore the async specific parameters
                    parameterSets.add(new Object[] { async, throwException, Boolean.FALSE,
                            AsyncErrorPoint.THREAD_A_AFTER_START_ASYNC, Boolean.FALSE} );
                }
            }
        }

        return parameterSets;
    }


    @Parameter(0)
    public boolean async;
    @Parameter(1)
    public boolean throwException;
    @Parameter(2)
    public boolean useDispatch;
    @Parameter(3)
    public AsyncErrorPoint errorPoint;
    @Parameter(4)
    public boolean useStart;


    @Test
    public void testSendError() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        if (async) {
            Wrapper w = Tomcat.addServlet(ctx, "target",
                    new TesterAsyncServlet(throwException, useDispatch, errorPoint, useStart));
            w.setAsyncSupported(true);
        } else {
            Tomcat.addServlet(ctx, "target", new TesterServlet(throwException));
        }
        ctx.addServletMappingDecoded("/target", "target");
        Tomcat.addServlet(ctx, "dispatch", new TesterDispatchServlet());
        ctx.addServletMappingDecoded("/dispatch", "dispatch");

        Tomcat.addServlet(ctx, "error599", new ErrorServletStatic599());
        ctx.addServletMappingDecoded("/error599", "error599");
        Tomcat.addServlet(ctx, "errorException", new ErrorServletStaticException());
        ctx.addServletMappingDecoded("/errorException", "errorException");

        ErrorPage ep1 = new ErrorPage();
        ep1.setErrorCode(599);
        ep1.setLocation("/error599");
        ctx.addErrorPage(ep1);

        ErrorPage ep2 = new ErrorPage();
        ep2.setExceptionType(SendErrorException.class.getName());
        ep2.setLocation("/errorException");
        ctx.addErrorPage(ep2);

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;

        rc = getUrl("http://localhost:" + getPort() + "/target", bc, null, null);

        String body = bc.toString();

        if (throwException) {
            Assert.assertEquals(500, rc);
            Assert.assertEquals("FAIL-Exception", body);
        } else {
            Assert.assertEquals(599, rc);
            Assert.assertEquals("FAIL-599", body);
        }
    }


    public static class TesterServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean throwException;

        public TesterServlet(boolean throwException) {
            this.throwException = throwException;
        }


        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            if (throwException) {
                throw new SendErrorException();
            } else {
                // Custom 5xx code so we can detect if the correct error is
                // reported
                resp.sendError(599);
            }
        }
    }


    public static class TesterAsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean throwException;
        private final  boolean useDispatch;
        private final AsyncErrorPoint errorPoint;
        private final  boolean useStart;

        public TesterAsyncServlet(boolean throwException, boolean useDispatch, AsyncErrorPoint errorPoint,
                boolean useStart) {
            this.throwException = throwException;
            this.useDispatch = useDispatch;
            this.errorPoint = errorPoint;
            this.useStart = useStart;
        }


        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            if (errorPoint == AsyncErrorPoint.THREAD_A_BEFORE_START_ASYNC) {
                doError(resp);
            }

            AsyncContext ac = req.startAsync();
            ac.setTimeout(2000);

            if (errorPoint == AsyncErrorPoint.THREAD_A_AFTER_START_ASYNC) {
                doError(resp);
            }

            AsyncRunnable r = new AsyncRunnable(ac, throwException, useDispatch, errorPoint);

            if (useStart) {
                ac.start(r);
            } else {
                Thread t = new Thread(r);
                t.start();
            }

            if (errorPoint == AsyncErrorPoint.THREAD_A_AFTER_START_RUNNABLE) {
                doError(resp);
            }
        }


        private void doError(HttpServletResponse resp) throws IOException {
            if (throwException) {
                throw new SendErrorException();
            } else {
                resp.sendError(599);
            }
        }
    }


    public static class AsyncRunnable implements Runnable {

        private final AsyncContext ac;
        private final boolean throwException;
        private final boolean useDispatch;
        private final AsyncErrorPoint errorPoint;

        public AsyncRunnable(AsyncContext ac, boolean throwException, boolean useDispatch,
                AsyncErrorPoint errorPoint) {
            this.ac = ac;
            this.throwException = throwException;
            this.useDispatch = useDispatch;
            this.errorPoint = errorPoint;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

/*
            if (errorPoint == AsyncErrorPoint.THREAD_B_AFTER_COMPLETE) {
                if (useDispatch) {
                    ac.complete();
                } else {
                    ac.dispatch("/dispatch");
                }
            }
*/
            if (throwException) {
                throw new SendErrorException();
            } else {
                // Custom 5xx code so we can detect if the correct error is
                // reported
                try {
                    ((HttpServletResponse) ac.getResponse()).sendError(599);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (errorPoint == AsyncErrorPoint.THREAD_B_BEFORE_COMPLETE) {
                if (useDispatch) {
                    ac.dispatch("/dispatch");
                } else {
                    ac.complete();
                }
            }
        }
    }


    public static class TesterDispatchServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("DISPATCH");
        }
    }


    public static class SendErrorException extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }


    public static class ErrorServletStatic599 extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("FAIL-599");
        }
    }


    public static class ErrorServletStaticException extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("FAIL-Exception");
        }
    }

}
