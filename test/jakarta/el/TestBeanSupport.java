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

import jakarta.el.BeanELResolver.BeanProperties;
import jakarta.el.BeanELResolver.BeanProperty;

import org.junit.Assert;
import org.junit.Test;

public class TestBeanSupport extends ELBaseTest {

    @Test
    public void testSimpleBean() {
        doTest(SimpleBean.class, "value", TypeA.class, TypeA.class, TypeA.class);
    }

    @Test
    public void testInvalidIs01Bean() {
        doTest(InvalidIs01Bean.class, "value", TypeA.class, TypeA.class, TypeA.class);
    }

    @Test
    public void testInvalidIs02Bean() {
        doTest(InvalidIs02Bean.class, "value", TypeA.class, null, TypeA.class);
    }

    @Test
    public void testInvalidIs03Bean() {
        doTest(InvalidIs03Bean.class, "value", TypeA.class, null, TypeA.class);
    }

    @Test
    public void testReadOnlyBean() {
        doTest(ReadOnlyBean.class, "value", TypeA.class, TypeA.class, null);
    }

    @Test
    public void testWriteOnlyBean() {
        doTest(WriteOnlyBean.class, "value", TypeA.class, null, TypeA.class);
    }

    @Test
    public void testOverLoadedWithGetABean() {
        doTest(OverLoadedWithGetABean.class, "value", TypeA.class, TypeA.class, TypeAAA.class);
    }

    @Test
    public void testOverLoadedWithGetAABean() {
        doTest(OverLoadedWithGetAABean.class, "value", TypeAA.class, TypeAA.class, TypeAAA.class);
    }

    @Test
    public void testOverLoadedWithGetAAABean() {
        doTest(OverLoadedWithGetAAABean.class, "value", TypeAAA.class, TypeAAA.class, TypeAAA.class);
    }

    @Test
    public void testMismatchBean() {
        doTest(MismatchBean.class, "value", TypeA.class, TypeA.class, null);
    }

    @Test
    public void testAmbiguousBean01() {
        doTest(AmbiguousBean01.class, "value", TypeA.class, null, TypeA.class);
    }

    @Test
    public void testAmbiguousBean02() {
        doTest(AmbiguousBean02.class, "value", TypeA.class, null, TypeA.class);
    }


    private void doTest(Class<?> clazz, String propertyName, Class<?> type, Class<?> typeGet, Class<?> typeSet) {
        BeanProperties beanProperties = BeanSupport.getInstance().getBeanProperties(clazz);
        BeanProperty beanProperty = beanProperties.properties.get(propertyName);

        Assert.assertNotNull(beanProperty);
        Assert.assertEquals(type, beanProperty.getPropertyType());

        if (typeGet == null) {
            Assert.assertNull(beanProperty.getReadMethod());
        } else {
            Assert.assertEquals(0, beanProperty.getReadMethod().getParameterCount());
            Assert.assertEquals(typeGet, beanProperty.getReadMethod().getReturnType());
        }

        if (typeSet == null) {
            Assert.assertNull(beanProperty.getWriteMethod());
        } else {
            Assert.assertEquals(void.class, beanProperty.getWriteMethod().getReturnType());
            Assert.assertEquals(1, beanProperty.getWriteMethod().getParameterCount());
            Assert.assertEquals(typeSet, beanProperty.getWriteMethod().getParameterTypes()[0]);
        }
    }


    public static class SimpleBean {
        public TypeA getValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }
    }


    public static class InvalidIs01Bean {
        public TypeA isValue() {
            return null;
        }

        public TypeA getValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }
    }


    public static class InvalidIs02Bean {
        public TypeA isValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }
    }


    public static class InvalidIs03Bean {
        public Boolean isValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }
    }


    public static class ReadOnlyBean {
        public TypeA getValue() {
            return null;
        }
    }


    public static class WriteOnlyBean {
        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }
    }


    public static class OverLoadedWithGetABean {
        public TypeA getValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }

        public void setValue(@SuppressWarnings("unused") TypeAA value) {
        }

        public void setValue(@SuppressWarnings("unused") TypeAAA value) {
        }
    }


    public static class OverLoadedWithGetAABean {
        public TypeAA getValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }

        public void setValue(@SuppressWarnings("unused") TypeAA value) {
        }

        public void setValue(@SuppressWarnings("unused") TypeAAA value) {
        }
    }


    public static class OverLoadedWithGetAAABean {
        public TypeAAA getValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }

        public void setValue(@SuppressWarnings("unused") TypeAA value) {
        }

        public void setValue(@SuppressWarnings("unused") TypeAAA value) {
        }
    }


    public static class MismatchBean {
        public TypeA getValue() {
            return null;
        }

        public void setValue(@SuppressWarnings("unused") String value) {
        }
    }


    public static class AmbiguousBean01 {
        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }

        public void setValue(@SuppressWarnings("unused") String value) {
        }
    }


    public static class AmbiguousBean02 {
        public void setValue(@SuppressWarnings("unused") String value) {
        }

        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }
    }


    public static class TypeA {
    }


    public static class TypeAA extends TypeA {
    }


    public static class TypeAAA extends TypeAA {
    }
}
