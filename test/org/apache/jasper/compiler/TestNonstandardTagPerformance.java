/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jasper.compiler;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ExpressionFactory;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.TesterPageContext;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.jasper.runtime.JspRuntimeLibrary;
//import org.apache.tomcat.InstanceManager;
//import org.apache.tomcat.SimpleInstanceManager;

/**
 * The benchmark requires libraries that are not normally on the build-time classpath (standard tags) so much of this
 * code is commented out. Users running the benchmark should uncomment the imports, fields, and methods.
 */
public class TestNonstandardTagPerformance {
    static final long NUM_ITERATIONS = 100000000L;

    static final int NUM_TESTS = 5;

    private ELContext elContext;

    private ExpressionFactory factory;

    // uncomment when running the benchmark
    // private InstanceManager instanceManager = new SimpleInstanceManager();

    private ELManager manager;
    public PageContext pageContext;

    public javax.el.ExpressionFactory _jsp_getExpressionFactory() {
        return factory;
    }

    // uncomment when running the benchmark
//    private InstanceManager _jsp_getInstanceManager() {
//        return instanceManager;
//    }

    private void newCode(javax.servlet.jsp.PageContext _jspx_page_context) throws java.lang.Throwable {
        JspRuntimeLibrary.nonstandardSetTag(_jspx_page_context, null, _jspx_page_context,
                javax.servlet.jsp.PageContext.PAGE_SCOPE);
        _jspx_page_context.setAttribute("groupName", new Object(), javax.servlet.jsp.PageContext.PAGE_SCOPE);
    }

//    /**
//     * This code is stolen from a generated JSP using standard c:set logic, except that the value is replaced by "new
//     * Object()". This eliminates the EL cost from the benchmark so we can accurately focus on the c:set performance.
//     *
//     * @param _jspx_th_c_005fwhen_005f11 parent tag
//     * @param _jspx_page_context page context
//     * @return whether to continue execution
//     * @throws java.lang.Throwable unknown error
//     */
//    private boolean oldCode(javax.servlet.jsp.tagext.JspTag _jspx_th_c_005fwhen_005f11,
//            javax.servlet.jsp.PageContext _jspx_page_context) throws java.lang.Throwable {
//        javax.servlet.jsp.PageContext pageContext = _jspx_page_context;
//        javax.servlet.jsp.JspWriter out = _jspx_page_context.getOut();
//        // c:set
//        org.apache.taglibs.standard.tag.rt.core.SetTag _jspx_th_c_005fset_005f39 = new org.apache.taglibs.standard.tag.rt.core.SetTag();
//        _jsp_getInstanceManager().newInstance(_jspx_th_c_005fset_005f39);
//        try {
//            _jspx_th_c_005fset_005f39.setPageContext(_jspx_page_context);
//            _jspx_th_c_005fset_005f39.setParent((javax.servlet.jsp.tagext.Tag) _jspx_th_c_005fwhen_005f11);
//            // /WEB-INF/views/jsp/features/buybox/offerDisplayGroupLayout.jsp(230,12) name = var type = java.lang.String
//            // reqTime = false required = false fragment = false deferredValue = false expectedTypeName = null
//            // deferredMethod = false methodSignature = null
//            _jspx_th_c_005fset_005f39.setVar("groupName");
//            // /WEB-INF/views/jsp/features/buybox/offerDisplayGroupLayout.jsp(230,12) name = value type =
//            // javax.el.ValueExpression reqTime = true required = false fragment = false deferredValue = true
//            // expectedTypeName = java.lang.Object deferredMethod = false methodSignature = null
//            _jspx_th_c_005fset_005f39.setValue(new Object());
//            int _jspx_eval_c_005fset_005f39 = _jspx_th_c_005fset_005f39.doStartTag();
//            if (_jspx_th_c_005fset_005f39.doEndTag() == javax.servlet.jsp.tagext.Tag.SKIP_PAGE) {
//                return true;
//            }
//        } finally {
//            org.apache.jasper.runtime.JspRuntimeLibrary.releaseTag(_jspx_th_c_005fset_005f39,
//                    _jsp_getInstanceManager());
//        }
//        return false;
//    }

    /**
     * This method depends on the availability of org.apache.taglibs.standard.tag.rt.core.SetTag, which is not typically
     * available for Tomcat's unit tests. To execute the benchmark correctly, please:
     * <ol>
     * <li>add a taglibs jar to the classpath</li>
     * <li>uncomment the body of {@code oldCode()}</li>
     * <li>run the test manually (IDE or command-line)</li>
     * <li>use jvm args similar to
     *
     * <pre>
     * -Xmx1g -Xms1g -verbose:gc -XX:+UseParallelGC
     * </pre>
     *
     * </li>
     * </ol>
     * @throws Throwable generic error
     */
    @Ignore
    @Test
    public void runBenchmark() throws Throwable {
        long[] durations = new long[NUM_TESTS];
        for (int i = 0; i < NUM_ITERATIONS / 10; i++) {
//            oldCode(null, pageContext);
            newCode(pageContext);
        }

        for (int i = 0; i < NUM_TESTS; i++) {
            long start = System.currentTimeMillis();
            for (long j = 0; j < NUM_ITERATIONS; j++) {
//                oldCode(null, pageContext);
            }
            durations[i] = System.currentTimeMillis() - start;
        }

        for (int d = 0; d < durations.length; d++) {
            System.out.println("Old: " + d + ". " + durations[d] + "ms");
        }
        for (int i = 0; i < NUM_TESTS; i++) {
            long start = System.currentTimeMillis();
            for (long j = 0; j < NUM_ITERATIONS; j++) {
                newCode(pageContext);
            }
            durations[i] = System.currentTimeMillis() - start;
        }

        for (int d = 0; d < durations.length; d++) {
            System.out.println("New: " + d + ". " + durations[d] + "ms");
        }
    }

    @Before
    public void setupTestVars() {
        manager = new ELManager();
        elContext = manager.getELContext();
        factory = ELManager.getExpressionFactory();
        pageContext = new TesterPageContext(elContext);
    }
}
