/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.tomcat.buildutil;

import java.io.IOException;
import java.io.Writer;

public class Utils {

    private Utils() {
        // Utility class. Hide default constructor.
    }


    public static void insertLicense(Writer w) throws IOException {
        w.write("# Licensed to the Apache Software Foundation (ASF) under one or more");
        w.write(System.lineSeparator());
        w.write("# contributor license agreements.  See the NOTICE file distributed with");
        w.write(System.lineSeparator());
        w.write("# this work for additional information regarding copyright ownership.");
        w.write(System.lineSeparator());
        w.write("# The ASF licenses this file to You under the Apache License, Version 2.0");
        w.write(System.lineSeparator());
        w.write("# (the \"License\"); you may not use this file except in compliance with");
        w.write(System.lineSeparator());
        w.write("# the License.  You may obtain a copy of the License at");
        w.write(System.lineSeparator());
        w.write("#");
        w.write(System.lineSeparator());
        w.write("#     http://www.apache.org/licenses/LICENSE-2.0");
        w.write(System.lineSeparator());
        w.write("#");
        w.write(System.lineSeparator());
        w.write("# Unless required by applicable law or agreed to in writing, software");
        w.write(System.lineSeparator());
        w.write("# distributed under the License is distributed on an \"AS IS\" BASIS,");
        w.write(System.lineSeparator());
        w.write("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.");
        w.write(System.lineSeparator());
        w.write("# See the License for the specific language governing permissions and");
        w.write(System.lineSeparator());
        w.write("# limitations under the License.");
        w.write(System.lineSeparator());
    }
}
