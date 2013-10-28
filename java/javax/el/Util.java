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
package javax.el;

class Util {

    /*
     * This method duplicates code in org.apache.el.util.ReflectionUtil. When
     * making changes keep the code in sync.
     */
    static boolean isAssignableFrom(Class<?> src, Class<?> target) {
        // src will always be an object
        // Short-cut. null is always assignable to an object and in EL null
        // can always be coerced to a valid value for a primitive
        if (src == null) {
            return true;
        }

        Class<?> targetClass;
        if (target.isPrimitive()) {
            if (target == Boolean.TYPE) {
                targetClass = Boolean.class;
            } else if (target == Character.TYPE) {
                targetClass = Character.class;
            } else if (target == Byte.TYPE) {
                targetClass = Byte.class;
            } else if (target == Short.TYPE) {
                targetClass = Short.class;
            } else if (target == Integer.TYPE) {
                targetClass = Integer.class;
            } else if (target == Long.TYPE) {
                targetClass = Long.class;
            } else if (target == Float.TYPE) {
                targetClass = Float.class;
            } else {
                targetClass = Double.class;
            }
        } else {
            targetClass = target;
        }
        return targetClass.isAssignableFrom(src);
    }
}
