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
package org.apache.tomcat.util.compat;

import java.lang.reflect.Field;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Jre19Compat extends Jre16Compat {

    private static final Log log = LogFactory.getLog(Jre19Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre19Compat.class);

    private static final boolean supported;

    static {
        // Don't need any Java 19 specific classes (yet) so just test for one of
        // the new ones for now.
        Class<?> c1 = null;
        try {
            c1 = Class.forName("java.lang.WrongThreadException");
        } catch (ClassNotFoundException cnfe) {
            // Must be pre-Java 16
            log.debug(sm.getString("jre19Compat.javaPre19"), cnfe);
        }

        supported = (c1 != null);
    }

    static boolean isSupported() {
        return supported;
    }

    @Override
    public Object getExecutor(Thread thread)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

        Object result = super.getExecutor(thread);

        if (result == null) {
            Object holder = null;
            Object task = null;
            try {
                Field holderField = thread.getClass().getDeclaredField("holder");
                holderField.setAccessible(true);
                holder = holderField.get(thread);

                Field taskField = holder.getClass().getDeclaredField("task");
                taskField.setAccessible(true);
                task = taskField.get(holder);
            } catch (NoSuchFieldException nfe) {
                return null;
            }

            if (task!= null && task.getClass().getCanonicalName() != null &&
                    (task.getClass().getCanonicalName().equals(
                            "org.apache.tomcat.util.threads.ThreadPoolExecutor.Worker") ||
                            task.getClass().getCanonicalName().equals(
                                    "java.util.concurrent.ThreadPoolExecutor.Worker"))) {
                Field executorField = task.getClass().getDeclaredField("this$0");
                executorField.setAccessible(true);
                result = executorField.get(task);
            }
        }

        return result;
    }
}
