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

import java.io.Serial;
import java.util.EventObject;

/**
 * Event object passed to {@link ELContextListener} instances when an EL context is created or
 * released. The source of this event is the {@link ELContext} that was created or released.
 *
 * @since EL 2.1
 */
public class ELContextEvent extends EventObject {

    @Serial
    private static final long serialVersionUID = 1255131906285426769L;

    /**
     * Constructs an ELContextEvent with the specified ELContext as its source.
     *
     * @param source The EL context that was the source of this event
     */
    public ELContextEvent(ELContext source) {
        super(source);
    }

    /**
     * Returns the ELContext that is the source of this event.
     *
     * @return the ELContext associated with this event
     */
    public ELContext getELContext() {
        return (ELContext) this.getSource();
    }

}
