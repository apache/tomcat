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
package org.apache.tomcat.unittest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

public class TesterLogValidationFilter implements Filter {

    private String targetMessage;
    private AtomicInteger messageCount = new AtomicInteger(0);


    public TesterLogValidationFilter(String targetMessage) {
        this.targetMessage = targetMessage;
    }


    public int getMessageCount() {
        return messageCount.get();
    }


    @Override
    public boolean isLoggable(LogRecord record) {
        String msg = record.getMessage();
        if (msg != null && msg.contains(targetMessage)) {
            messageCount.incrementAndGet();
        }

        return true;
    }
}
