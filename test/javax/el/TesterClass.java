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

public class TesterClass {

    public static final String publicStaticString = "publicStaticString";
    public String publicString = "publicString";
    @SuppressWarnings("unused") // Used in TestStaticFieldELResolver
    private static String privateStaticString = "privateStaticString";
    @SuppressWarnings("unused") // Used in TestStaticFieldELResolver
    private String privateString = "privateString";

    public TesterClass() {
    }

    @SuppressWarnings("unused") // Used in TestStaticFieldELResolver
    private TesterClass(String privateString) {
        this.privateString = privateString;
    }

    public static String getPublicStaticString() {
        return publicStaticString;
    }

    public static void printPublicStaticString() {
        System.out.println(publicStaticString);
    }

    public void setPrivateString(String privateString) {
        this.privateString = privateString;
    }
}
