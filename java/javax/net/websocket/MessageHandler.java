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
package javax.net.websocket;

import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

public interface MessageHandler {

    interface Text extends MessageHandler {

        void onMessage(String text);
    }


    interface Binary extends MessageHandler {

        void onMessage(ByteBuffer data);
    }


    interface AsyncBinary extends MessageHandler {

         void onMessagePart(ByteBuffer part, boolean last);
    }


    interface AsyncText extends MessageHandler {

        void onMessagePart(String part, boolean last);
    }


    interface DecodedObject<T> extends MessageHandler {

        void onMessage(T customObject);
    }

    interface BinaryStream extends MessageHandler {

        void onMessage(InputStream is);
    }


    interface CharacterStream extends MessageHandler {

        void onMessage(Reader r);
    }


    interface Pong extends MessageHandler {

        void onPong(ByteBuffer applicationData);
    }
}
