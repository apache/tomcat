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

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.StandardELContext;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.TesterPageContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/*
 * The entries of the implicit object scope maps must honour the Map.Entry equals() contract rather than deciding
 * equality from the hash code of the key alone.
 */
public class TestImplicitObjectELResolver {

    private ELContext context;

    @Before
    public void setUp() {
        PageContext page = new AttributePageContext();
        page.setAttribute("Aa", "value1");
        page.setAttribute("BB", "value2");
        page.setAttribute("other", "value3");

        context = new StandardELContext(ExpressionFactory.newInstance());
        context.putContext(JspContext.class, page);
    }


    @SuppressWarnings("unchecked")
    private Set<Map.Entry<String,Object>> getPageScopeEntries() {
        Object pageScope = new ImplicitObjectELResolver().getValue(context, null, "pageScope");
        Assert.assertTrue(pageScope instanceof Map);
        return ((Map<String,Object>) pageScope).entrySet();
    }


    private Map.Entry<String,Object> getEntry(String key) {
        for (Map.Entry<String,Object> entry : getPageScopeEntries()) {
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }
        Assert.fail("No entry for key [" + key + "]");
        return null;
    }


    @Test
    public void testEntrySetContents() {
        Assert.assertEquals(3, getPageScopeEntries().size());
        Assert.assertEquals("value1", getEntry("Aa").getValue());
    }


    @Test
    public void testEntryEqualsSameKey() {
        Assert.assertEquals(getEntry("other"), getEntry("other"));
        Assert.assertEquals(getEntry("other").hashCode(), getEntry("other").hashCode());
    }


    @Test
    public void testEntryNotEqualsHashCollidingKeys() {
        // "Aa" and "BB" are a well known pair of strings with the same hash code
        Assert.assertEquals("Aa".hashCode(), "BB".hashCode());

        Assert.assertNotEquals(getEntry("Aa"), getEntry("BB"));
    }


    @Test
    public void testEntryNotEqualsOtherType() {
        Map.Entry<String,Object> entry = getEntry("Aa");

        // A String with the same hash code as the key is not an equal Map.Entry
        Assert.assertNotEquals(entry, "Aa");
        Assert.assertNotEquals(entry, null);
    }


    @Test
    public void testEntryEqualsGenericEntry() {
        // Map.Entry contract: equal if the keys and values are equal, whatever the implementation
        Map.Entry<String,Object> entry = getEntry("Aa");
        Map.Entry<String,Object> generic = Collections.singletonMap("Aa", (Object) "value1").entrySet().iterator().next();
        Map.Entry<String,Object> differentValue =
                Collections.singletonMap("Aa", (Object) "other").entrySet().iterator().next();

        Assert.assertEquals(generic, entry);
        Assert.assertEquals(generic.hashCode(), entry.hashCode());
        Assert.assertNotEquals(differentValue, entry);
    }


    @Test
    public void testEntrySetHashCollidingKeysBothPresent() {
        Set<Map.Entry<String,Object>> set = new HashSet<>(getPageScopeEntries());
        Assert.assertEquals(3, set.size());
    }


    private static class AttributePageContext extends TesterPageContext {

        private final Map<String,Object> attributes = new HashMap<>();

        @Override
        public void setAttribute(String name, Object value) {
            // The resolver stores its own scope manager as a page attribute. Only keep the test's attributes.
            if (value instanceof String) {
                attributes.put(name, value);
            }
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNamesInScope(int scope) {
            return Collections.enumeration(new HashSet<>(attributes.keySet()));
        }
    }
}
