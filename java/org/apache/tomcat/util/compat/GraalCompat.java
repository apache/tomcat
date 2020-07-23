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

import java.io.IOException;

class GraalCompat extends Jre9Compat {

    private static final boolean GRAAL;

    static {
        boolean result = false;
        try {
            Class<?> nativeImageClazz = Class.forName("org.graalvm.nativeimage.ImageInfo");
            result = Boolean.TRUE.equals(nativeImageClazz.getMethod("inImageCode").invoke(null));
        } catch (ClassNotFoundException e) {
            // Must be Graal
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Should never happen
        }
        GRAAL = result || System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    static boolean isSupported() {
        // This property does not exist for a native image
        return GRAAL;
    }

    @Override
    public void disableCachingForJarUrlConnections() throws IOException {
    }

}
