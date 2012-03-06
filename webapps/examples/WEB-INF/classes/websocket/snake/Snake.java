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
package websocket.snake;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

import org.apache.catalina.websocket.WsOutbound;

public class Snake {

    private static final int DEFAULT_LENGTH = 6;

    private final int id;
    private final WsOutbound outbound;

    private Direction direction;
    private Deque<Location> locations = new ArrayDeque<Location>();
    private String hexColor;

    public Snake(int id, WsOutbound outbound) {
        this.id = id;
        this.outbound = outbound;
        this.hexColor = SnakeWebSocketServlet.getRandomHexColor();
        resetState();
    }

    private void resetState() {
        this.direction = Direction.NONE;
        this.locations.clear();
        Location startLocation = SnakeWebSocketServlet.getRandomLocation();
        for (int i = 0; i < DEFAULT_LENGTH; i++) {
            locations.add(startLocation);
        }
    }

    private void kill() {
        resetState();
        try {
            CharBuffer response = CharBuffer.wrap("{'type': 'dead'}");
            outbound.writeTextMessage(response);
        } catch (IOException ioe) {
            // Ignore
        }
    }

    private void reward() {
        grow();
        try {
            CharBuffer response = CharBuffer.wrap("{'type': 'kill'}");
            outbound.writeTextMessage(response);
        } catch (IOException ioe) {
            // Ignore
        }
    }

    public synchronized void update(Collection<Snake> snakes) {
        Location firstLocation = locations.getFirst();
        Location nextLocation = firstLocation.getAdjacentLocation(direction);
        if (nextLocation.x >= SnakeWebSocketServlet.PLAYFIELD_WIDTH) {
            nextLocation.x = 0;
        }
        if (nextLocation.y >= SnakeWebSocketServlet.PLAYFIELD_HEIGHT) {
            nextLocation.y = 0;
        }
        if (nextLocation.x < 0) {
            nextLocation.x = SnakeWebSocketServlet.PLAYFIELD_WIDTH;
        }
        if (nextLocation.y < 0) {
            nextLocation.y = SnakeWebSocketServlet.PLAYFIELD_HEIGHT;
        }
        locations.addFirst(nextLocation);
        locations.removeLast();

        for (Snake snake : snakes) {
            if (snake.getId() != getId() &&
                    colliding(snake.getHeadLocation())) {
                snake.kill();
                reward();
            }
        }
    }

    private void grow() {
        Location lastLocation = locations.getLast();
        Location newLocation = new Location(lastLocation.x, lastLocation.y);
        locations.add(newLocation);
    }

    private boolean colliding(Location location) {
        return direction != Direction.NONE && locations.contains(location);
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public synchronized String getLocationsJson() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<Location> iterator = locations.iterator();
                iterator.hasNext();) {
            Location location = iterator.next();
            sb.append(String.format("{x: %d, y: %d}",
                    Integer.valueOf(location.x), Integer.valueOf(location.y)));
            if (iterator.hasNext()) {
                sb.append(',');
            }
        }
        return String.format("{'id':%d,'body':[%s]}",
                Integer.valueOf(id), sb.toString());
    }

    public int getId() {
        return id;
    }

    public String getHexColor() {
        return hexColor;
    }

    public synchronized Location getHeadLocation() {
        return locations.getFirst();
    }
}
