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
package org.apache.tomcat.websocket;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import websocket.drawboard.DrawMessage;

public class TestDrawMessage {

    @Test
    public void testAxisAlignedRectangleDrawnAsLine() {
        assertDrawsLine(3, 5, 20, 5, 30);
    }

    @Test
    public void testAxisAlignedEllipseDrawnAsLine() {
        assertDrawsLine(4, 20, 5, 30, 5);
    }

    private static void assertDrawsLine(int type, double x1, double y1, double x2, double y2) {
        Graphics2D graphics = EasyMock.createNiceMock(Graphics2D.class);
        Capture<Shape> shape = EasyMock.newCapture();
        graphics.draw(EasyMock.capture(shape));
        EasyMock.expectLastCall().once();
        EasyMock.replay(graphics);

        DrawMessage message = new DrawMessage(type, (byte) 0, (byte) 0, (byte) 0, (byte) 0, 1, x1, x2, y1, y2);
        message.draw(graphics);

        EasyMock.verify(graphics);
        Assert.assertTrue(shape.getValue() instanceof Line2D);
    }
}
