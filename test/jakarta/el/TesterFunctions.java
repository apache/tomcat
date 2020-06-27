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

public class TesterFunctions {

    private static StringBuilder calls = new StringBuilder();

    public static String getCallList() {
        return calls.toString();
    }

    public static void resetCallList() {
        calls = new StringBuilder();
    }

    public static void doIt() {
        calls.append('A');
    }

    public static void doIt(@SuppressWarnings("unused") int a) {
        calls.append('B');
    }

    public static void doIt(@SuppressWarnings("unused") Integer a) {
        calls.append('C');
    }

    public static void doIt(@SuppressWarnings("unused") int[] a) {
        calls.append('D');
    }

    public static void doIt(@SuppressWarnings("unused") int[][] a) {
        calls.append('E');
    }

    public static void doIt(@SuppressWarnings("unused") Integer[] a) {
        calls.append('F');
    }

    public static void doIt(@SuppressWarnings("unused") Integer[][] a) {
        calls.append('G');
    }

    public static void doIt(@SuppressWarnings("unused") long... a) {
        calls.append('H');
    }

    public static void doIt(@SuppressWarnings("unused") Object... a) {
        calls.append('I');
    }
}
