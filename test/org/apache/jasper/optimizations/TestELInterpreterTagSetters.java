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
package org.apache.jasper.optimizations;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.SimpleTagSupport;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.ELInterpreter;
import org.apache.jasper.compiler.ELInterpreterFactory;
import org.apache.jasper.compiler.StringInterpreter;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestELInterpreterTagSetters extends TomcatBaseTest {

    private static final Integer ZERO = Integer.valueOf(0);
    private static final Integer TWO = Integer.valueOf(2);
    private static final Integer THREE = Integer.valueOf(3);

    @Parameters(name="{index}: {0} {1} {3},{4}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        ELInterpreter[] elInterpreters = new ELInterpreter[] {
                // First call will trigger compilation (and therefore be slower)
                // For performance tests call each once to warm-up and once to
                // test
                new ELInterpreterWrapper(true, "TagSetters"),
                new ELInterpreterWrapper(false, "Default"),
                // Uncomment for a performance test and compare times of these
                // test runs
                //new ELInterpreterWrapper(true, "TagSetters"),
                //new ELInterpreterWrapper(false, "Default"),
                };

        for (ELInterpreter elInterpreter : elInterpreters) {
            parameterSets.add(new Object[] { elInterpreter, "boolean", "false", ZERO, THREE });
            parameterSets.add(new Object[] { elInterpreter, "boolean", "true", THREE, THREE });
            parameterSets.add(new Object[] { elInterpreter, "primitive-boolean", "false", ZERO, THREE });
            parameterSets.add(new Object[] { elInterpreter, "primitive-boolean", "true", THREE, THREE });
            parameterSets.add(new Object[] { elInterpreter, "character", "b", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-character", "b", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "bigdecimal", "12.34", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "biginteger", "1234", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "long", "1234", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-long", "1234", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "integer", "1234", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-integer", "1234", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "short", "1234", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-short", "1234", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "byte", "12", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "byte", "-12", TWO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-byte", "12", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-byte", "-12", TWO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "double", "12.34", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-double", "12.34", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "float", "12.34", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "primitive-float", "12.34", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "timeunit", "SECONDS", ZERO, TWO });
            parameterSets.add(new Object[] { elInterpreter, "string", "bar", ZERO, TWO });
        }
        return parameterSets;
    }


    @Parameter(0)
    public ELInterpreter elInterpreter;
    @Parameter(1)
    public String target;
    @Parameter(2)
    public String expectedValue;
    @Parameter(3)
    public int offset;
    @Parameter(4)
    public int len;


    @Test
    public void testTag() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, true);
        Context ctxt = (Context) tomcat.getHost().findChild("/test");
        ctxt.getServletContext().setAttribute(ELInterpreter.class.getCanonicalName(), elInterpreter);

        ctxt.getServletContext().setAttribute(StringInterpreter.class.getCanonicalName(), new StringInterpreterEnum());

        // Change this to 1000000 to test performance
        String iterations = "1";

        ByteChunk bc = getUrl("http://localhost:" + getPort() +
                "/test/bug6nnnn/bug64872-" + target + ".jsp?iterations=" + iterations);

        String actual = bc.toString();

        for (int i = offset; i < offset + len; i++) {
            String expected = String.format("%02d The value of foo is [%s]", Integer.valueOf(i+1), expectedValue);
            Assert.assertTrue(actual, actual.contains(expected));
        }
    }


    public static class TagPrimitiveBoolean extends SimpleTagSupport {

        private boolean foo;

        public boolean getFoo() {
            return foo;
        }

        public void setFoo(boolean foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagBoolean extends SimpleTagSupport {

        private Boolean foo;

        public Boolean getFoo() {
            return foo;
        }

        public void setFoo(Boolean foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagPrimitiveCharacter extends SimpleTagSupport {

        private char foo;

        public char getFoo() {
            return foo;
        }

        public void setFoo(char foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagCharacter extends SimpleTagSupport {

        private Character foo;

        public Character getFoo() {
            return foo;
        }

        public void setFoo(Character foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagPrimitiveLong extends SimpleTagSupport {

        private long foo;

        public long getFoo() {
            return foo;
        }

        public void setFoo(long foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagLong extends SimpleTagSupport {

        private Long foo;

        public Long getFoo() {
            return foo;
        }

        public void setFoo(Long foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagPrimitiveInteger extends SimpleTagSupport {

        private int foo;

        public int getFoo() {
            return foo;
        }

        public void setFoo(int foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagInteger extends SimpleTagSupport {

        private Integer foo;

        public Integer getFoo() {
            return foo;
        }

        public void setFoo(Integer foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagPrimitiveShort extends SimpleTagSupport {

        private short foo;

        public short getFoo() {
            return foo;
        }

        public void setFoo(short foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagShort extends SimpleTagSupport {

        private Short foo;

        public Short getFoo() {
            return foo;
        }

        public void setFoo(Short foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagPrimitiveByte extends SimpleTagSupport {

        private byte foo;

        public byte getFoo() {
            return foo;
        }

        public void setFoo(byte foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagByte extends SimpleTagSupport {

        private Byte foo;

        public Byte getFoo() {
            return foo;
        }

        public void setFoo(Byte foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagPrimitiveDouble extends SimpleTagSupport {

        private double foo;

        public double getFoo() {
            return foo;
        }

        public void setFoo(double foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagDouble extends SimpleTagSupport {

        private Double foo;

        public Double getFoo() {
            return foo;
        }

        public void setFoo(Double foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagPrimitiveFloat extends SimpleTagSupport {

        private float foo;

        public float getFoo() {
            return foo;
        }

        public void setFoo(float foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagFloat extends SimpleTagSupport {

        private Float foo;

        public Float getFoo() {
            return foo;
        }

        public void setFoo(Float foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagString extends SimpleTagSupport {

        private String foo;

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagTimeUnit extends SimpleTagSupport {

        private TimeUnit foo;

        public TimeUnit getFoo() {
            return foo;
        }

        public void setFoo(TimeUnit foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagBigDecimal extends SimpleTagSupport {

        private BigDecimal foo;

        public BigDecimal getFoo() {
            return foo;
        }

        public void setFoo(BigDecimal foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    public static class TagBigInteger extends SimpleTagSupport {

        private BigInteger foo;

        public BigInteger getFoo() {
            return foo;
        }

        public void setFoo(BigInteger foo) {
            this.foo = foo;
        }

        @Override
        public void doTag() throws JspException, IOException {
            getJspContext().getOut().print(foo);
        }
    }


    /*
     * Wrapper so we can use sensible names in the test labels
     */
    private static class ELInterpreterWrapper implements ELInterpreter {

        private final boolean optimised;
        private final String name;
        private volatile ELInterpreter elInterpreter = null;

        public ELInterpreterWrapper(boolean optimised, String name) {
            this.optimised = optimised;
            this.name = name;
        }

        @Override
        public String interpreterCall(JspCompilationContext context, boolean isTagFile,
                String expression, Class<?> expectedType, String fnmapvar) {
            return getElInterpreter().interpreterCall(context, isTagFile, expression, expectedType, fnmapvar);
        }

        @Override
        public String toString() {
            return name;
        }

        // Lazy init to avoid LogManager init issues when running parameterized tests
        private ELInterpreter getElInterpreter() {
            if (elInterpreter == null) {
                synchronized (this) {
                    if (elInterpreter == null) {
                        if (optimised) {
                            elInterpreter = new ELInterpreterTagSetters();
                        } else {
                            elInterpreter = new ELInterpreterFactory.DefaultELInterpreter();
                        }
                    }
                }
            }
            return elInterpreter;
        }
    }
}
