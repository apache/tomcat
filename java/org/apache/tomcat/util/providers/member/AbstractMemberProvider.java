/**
 *  Copyright 2017 Ismaïl Senhaji, Guillaume Pythoud, Maxime Beck
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.providers.member;

import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.tomcat.util.providers.stream.StreamProvider;

public abstract class AbstractMemberProvider implements MemberProvider {
    private static final Logger log = Logger.getLogger(AbstractMemberProvider.class.getName());

    private String url;
    private StreamProvider streamProvider;
    private int connectionTimeout;
    private int readTimeout;

    private Instant startTime;
    private MessageDigest md5;

    private Map<String, String> headers = new HashMap<>();

    private int port;
    private String hostName;

    public AbstractMemberProvider() {
        try {
            md5 = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            // Ignore
        }
    }

    // Get value of environment variable named keys[0]
    // If keys[0] isn't found, try keys[1], keys[2], ...
    // If nothing is found, return null
    private static String getEnv(String... keys) {
        String val = null;

        for (String key : keys) {
            val = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getenv(key));
            if (val != null)
                break;
        }

        return val;
    }
}
