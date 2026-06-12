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
package org.apache.catalina.ant.jmx;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.junit.Assert;
import org.junit.Test;

public class TestJMXAccessorTask {

    @Test
    public void testCreatePropertyForTabularDataSupport() throws Exception {
        JMXAccessorTask task = new JMXAccessorTask();

        task.createProperty("tabular", createTabularData());

        Assert.assertEquals("0", task.getProperty("tabular.0.id"));
        Assert.assertEquals("alpha", task.getProperty("tabular.0.details.name"));
        Assert.assertEquals("7", task.getProperty("tabular.0.count"));
        Assert.assertEquals("1", task.getProperty("tabular.1.id"));
        Assert.assertEquals("beta", task.getProperty("tabular.1.details.name"));
        Assert.assertEquals("11", task.getProperty("tabular.1.count"));
    }


    private static TabularDataSupport createTabularData() throws OpenDataException {
        CompositeType detailsType = new CompositeType("details", "details", new String[] { "name" },
                new String[] { "name" }, new OpenType<?>[] { SimpleType.STRING });
        CompositeType rowType = new CompositeType("row", "row", new String[] { "id", "details", "count" },
                new String[] { "id", "details", "count" },
                new OpenType<?>[] { SimpleType.STRING, detailsType, SimpleType.INTEGER });
        TabularDataSupport tabularData = new TabularDataSupport(new TabularType("table", "table", rowType,
                new String[] { "id" }));

        tabularData.put(createRow(rowType, detailsType, "0", "alpha", Integer.valueOf(7)));
        tabularData.put(createRow(rowType, detailsType, "1", "beta", Integer.valueOf(11)));

        return tabularData;
    }


    private static CompositeDataSupport createRow(CompositeType rowType, CompositeType detailsType, String id,
            String detailName, Integer count) throws OpenDataException {
        CompositeDataSupport details = new CompositeDataSupport(detailsType, new String[] { "name" },
                new Object[] { detailName });

        return new CompositeDataSupport(rowType, new String[] { "id", "details", "count" },
                new Object[] { id, details, count });
    }
}
