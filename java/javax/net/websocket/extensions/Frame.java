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
package javax.net.websocket.extensions;

import javax.net.websocket.CloseReason;

public interface Frame {

    interface Data extends Frame {

        byte[] getExtensionData();

        interface Text extends Frame.Data {

            String getText();

            interface Continuation extends Frame.Data.Text {

                boolean isLast();
            }
        }

        interface Binary extends Frame.Data {

            byte[] getData();

            interface Continuation extends Frame.Data.Binary {

                boolean isLast();
            }
        }
    }

    interface Control extends Frame {

        interface Ping extends Frame.Control  {

            byte[] getApplicationData();
        }

        interface Pong extends Frame.Control {

            byte[] getApplicationData();
        }

        interface Close extends Frame.Control {

            String getReasonPhrase();

            CloseReason.CloseCode getCloseCode();
        }
    }
}
