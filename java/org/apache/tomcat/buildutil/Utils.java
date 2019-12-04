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

    private static final String LINE_SEP = System.getProperty("line.separator");


    private Utils() {
        // Utility class. Hide default constructor.
    }


    public static void insertLicense(Writer w) throws IOException {
        w.write("# Licensed to the Apache Software Foundation (ASF) under one or more");
        w.write(LINE_SEP);
        w.write("# contributor license agreements.  See the NOTICE file distributed with");
        w.write(LINE_SEP);
        w.write("# this work for additional information regarding copyright ownership.");
        w.write(LINE_SEP);
        w.write("# The ASF licenses this file to You under the Apache License, Version 2.0");
        w.write(LINE_SEP);
        w.write("# (the \"License\"); you may not use this file except in compliance with");
        w.write(LINE_SEP);
        w.write("# the License.  You may obtain a copy of the License at");
        w.write(LINE_SEP);
        w.write("#");
        w.write(LINE_SEP);
        w.write("#     http://www.apache.org/licenses/LICENSE-2.0");
        w.write(LINE_SEP);
        w.write("#");
        w.write(LINE_SEP);
        w.write("# Unless required by applicable law or agreed to in writing, software");
        w.write(LINE_SEP);
        w.write("# distributed under the License is distributed on an \"AS IS\" BASIS,");
        w.write(LINE_SEP);
        w.write("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.");
        w.write(LINE_SEP);
        w.write("# See the License for the specific language governing permissions and");
        w.write(LINE_SEP);
        w.write("# limitations under the License.");
        w.write(LINE_SEP);
    }
}
