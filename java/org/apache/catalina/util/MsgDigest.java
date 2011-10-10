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
package org.apache.catalina.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Helper class for generating message digests in multi-threaded environments.
 */
public class MsgDigest {
    
    private static Helper md5 = new Helper("MD5");
    private static Helper sha1 = new Helper("SHA1");

    public static byte[] getMD5(byte[] input) {
        return md5.digest(input);
    }

    public static byte[] getSHA1(byte[] input) {
        return sha1.digest(input);
    }
    
    private static class Helper {
        
        private final String digestName;
        private final Queue<MessageDigest> digesters =
                new ConcurrentLinkedQueue<MessageDigest>();
        
        public Helper(String digestName) {
            this.digestName = digestName;
        }
        
        public byte[] digest(byte[] input) {
            
            // Get a digest from the queue
            MessageDigest digest = digesters.poll();
            if (digest == null) {
                // Queue empty, create a new one
                try {
                    digest = MessageDigest.getInstance(digestName);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            
            // Reset the digest before we use it
            digest.reset();
            
            // Create the digest for the provided input
            digest.update(input);
            byte[] result = digest.digest();
            
            // Return the digester to the queue
            digesters.add(digest);
            
            return result;
        }
    }
}
