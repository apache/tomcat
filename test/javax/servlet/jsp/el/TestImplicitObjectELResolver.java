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

package javax.servlet.jsp.el;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.el.ELContext;
import javax.el.PropertyNotWritableException;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;
import java.beans.FeatureDescriptor;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.niceMock;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class TestImplicitObjectELResolver {

    private final String property;
    private ImplicitObjectELResolver resolver;
    private ELContext context;
    private PageContext pageContext;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    public TestImplicitObjectELResolver(String property) {
        this.property = property;
    }

    @Before
    public void setUp() {
        resolver = new ImplicitObjectELResolver();
        context = niceMock(ELContext.class);
        pageContext = niceMock(PageContext.class);

    }

    @Parameterized.Parameters
    public static List<String> testProperty() {
        return Arrays.asList(null, "applicationScope", "cookie", "header", "headerValues", "initParam", "pageContext", "pageScope", "param", "paramValues", "requestScope", "sessionScope");
    }

    @Test
    public void testGetValue() {

        // Set Mock Expectations
        EasyMock.expect(context.getContext(JspContext.class)).andReturn(pageContext);
        context.setPropertyResolved(null, property);
        EasyMock.expectLastCall().once();
        EasyMock.expect(pageContext.getAttribute(anyString())).andReturn(null);
        EasyMock.replay(pageContext);
        EasyMock.replay(context);


        // Run the method
        Object result = resolver.getValue(context, null, property);

        // Verify the interactions, and that the result was as expected
        if (property == null) {
            assertNull(result);
        } else {
            assertNotNull(result);
        }
    }

    @Test
    public void testGetType() {

        // Set Mock Expectations
        EasyMock.expect(context.getContext(JspContext.class)).andReturn(pageContext);
        context.setPropertyResolved(null, property);
        EasyMock.expectLastCall().once();
        EasyMock.replay(context);


        // Run the method
        Object result = resolver.getType(context, null, property);

        // Verify the interactions, and that the result was as expected
        assertNull(result);

    }

    @Test
    public void testSetValue() {
        // Set Mock Expectations
        EasyMock.expect(context.getContext(JspContext.class)).andReturn(pageContext);
        context.setPropertyResolved(null, property);
        EasyMock.expectLastCall().once();
        EasyMock.replay(context);

        // Verify the interactions, and that the result was as expected
        if (property != null) {
            exception.expect(PropertyNotWritableException.class);
        }

        // Run the method
        resolver.setValue(context, null, property, new Object());

    }


    @Test
    public void testIsReadOnly() {

        // Set Mock Expectations
        EasyMock.expect(context.getContext(JspContext.class)).andReturn(pageContext);
        context.setPropertyResolved(null, property);
        EasyMock.expectLastCall().once();
        EasyMock.replay(context);


        // Run the method
        boolean result = resolver.isReadOnly(context, null, property);

        // Verify the interactions, and that the result was as expected
        if (property == null) {
            assertFalse(result);
        } else {
            assertTrue(result);
        }
    }

    @Test
    public void testGetFeatureDescriptors() {
        // Set Mock Expectations
        EasyMock.expect(context.getContext(JspContext.class)).andReturn(pageContext);
        context.setPropertyResolved(null, property);
        EasyMock.expectLastCall().once();
        EasyMock.replay(context);


        // Run the method
        Iterator<FeatureDescriptor> featureDescriptors = resolver.getFeatureDescriptors(context, null);

        // Verify the interactions, and that the result was as expected
        assertTrue(featureDescriptors.hasNext());

    }

    @Test
    public void testGetCommonPropertyType_NullBase() {
        // Set Mock Expectations
        EasyMock.expect(context.getContext(JspContext.class)).andReturn(pageContext);
        context.setPropertyResolved(null, property);
        EasyMock.expectLastCall().once();
        EasyMock.replay(context);


        // Run the method
        Class<String> result = resolver.getCommonPropertyType(context, null);

        // Verify the interactions, and that the result was as expected
        assertNotNull(result);
    }

    @Test
    public void testGetCommonPropertyType_StringBase() {
        // Set Mock Expectations
        EasyMock.expect(context.getContext(JspContext.class)).andReturn(pageContext);
        context.setPropertyResolved(null, property);
        EasyMock.expectLastCall().once();
        EasyMock.replay(context);


        // Run the method
        Class<String> result = resolver.getCommonPropertyType(context, "null");

        // Verify the interactions, and that the result was as expected
        assertNull(result);

    }


}