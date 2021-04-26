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
package jakarta.el;

import org.junit.Assert;
import org.junit.Test;

public class TestELResolver {

    @Test
    public void testConvertToType01() {
        ELContext context = new TesterELContext();

        ValueExpression ve =
                ELManager.getExpressionFactory().createValueExpression(
                        context, "1", String.class);

        String result = (String) ve.getValue(context);

        Assert.assertEquals("1", result);
    }


    @Test
    public void testConvertToType02() {
        ELContext context = new TesterELContext(new TesterELResolverOne());

        ValueExpression ve =
                ELManager.getExpressionFactory().createValueExpression(
                        context, "1", String.class);

        String result = (String) ve.getValue(context);

        Assert.assertEquals("ONE", result);
    }


    @Test
    public void testConvertToType03() {
        ELContext context = new TesterELContext(new TesterELResolverOne());

        ValueExpression ve =
                ELManager.getExpressionFactory().createValueExpression(
                        context, "2", String.class);

        String result = (String) ve.getValue(context);

        Assert.assertEquals("2", result);
    }


    @Test
    public void testConvertToType04() {
        CompositeELResolver resolver = new CompositeELResolver();
        ELContext context = new TesterELContext(resolver);

        ValueExpression ve =
                ELManager.getExpressionFactory().createValueExpression(
                        context, "2", String.class);

        String result = (String) ve.getValue(context);

        Assert.assertEquals("2", result);
    }


    @Test
    public void testConvertToType05() {
        CompositeELResolver resolver = new CompositeELResolver();
        resolver.add(new TesterELResolverOne());
        resolver.add(new TesterELResolverTwo());
        ELContext context = new TesterELContext(resolver);

        ValueExpression ve =
                ELManager.getExpressionFactory().createValueExpression(
                        context, "1", String.class);

        String result = (String) ve.getValue(context);

        Assert.assertEquals("ONE", result);
    }


    @Test
    public void testConvertToType06() {
        CompositeELResolver resolver = new CompositeELResolver();
        resolver.add(new TesterELResolverOne());
        resolver.add(new TesterELResolverTwo());
        ELContext context = new TesterELContext(resolver);

        ValueExpression ve =
                ELManager.getExpressionFactory().createValueExpression(
                        context, "2", String.class);

        String result = (String) ve.getValue(context);

        Assert.assertEquals("TWO", result);
    }


    @Test
    public void testConvertToType07() {
        CompositeELResolver resolver = new CompositeELResolver();
        resolver.add(new TesterELResolverOne());
        resolver.add(new TesterELResolverTwo());
        ELContext context = new TesterELContext(resolver);

        ValueExpression ve =
                ELManager.getExpressionFactory().createValueExpression(
                        context, "3", String.class);

        String result = (String) ve.getValue(context);

        Assert.assertEquals("3", result);
    }

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=57802
    @Test
    public void testDefaultConvertToType() {
        ELContext context = new TesterELContext(new StaticFieldELResolver());

        ValueExpression ve = ELManager.getExpressionFactory().createValueExpression(
                        context, "${!Boolean.FALSE}", Boolean.class);

        Boolean result = (Boolean) ve.getValue(context);

        Assert.assertEquals(Boolean.TRUE, result);
    }
}
