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
package org.apache.coyote.servlet;


import junit.framework.Test;

public class LiteWatchdogJspTests  extends LiteWatchdogServletTests {
    
    public LiteWatchdogJspTests() {
        super();
        port = 8017;
        testMatch = 
            //"precompileNegativeTest";
            null;
        // Test we know are failing - need to fix at some point.
        exclude = new String[] {
                "negativeDuplicateExtendsFatalTranslationErrorTest",
                "negativeDuplicateErrorPageFatalTranslationErrorTest",
                "negativeDuplicateInfoFatalTranslationErrorTest",
                "negativeDuplicateLanguageFatalTranslationErrorTest",
                "negativeDuplicateSessionFatalTranslationErrorTest",
                "positiveIncludeCtxRelativeHtmlTest",
                "precompileNegativeTest"
            }; 
        file = getWatchdogdir() + "/src/conf/jsp-gtest.xml";
        goldenDir = 
            getWatchdogdir() + "/src/clients/org/apache/jcheck/jsp/client/";
        targetMatch = "jsp-test";
        
    }
    
    public static Test suite() {
        return new LiteWatchdogJspTests().getSuite(8017);
    }
    
}

