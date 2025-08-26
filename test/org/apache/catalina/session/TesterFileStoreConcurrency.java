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
package org.apache.catalina.session;

import java.io.File;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.startup.ExpandWar;
import org.apache.tomcat.unittest.TesterContext;

/*
 * Test for https://bz.apache.org/bugzilla/show_bug.cgi?id=69781
 *
 * The test currently fails almost instantly on markt's desktop with a 5s run time.
 *
 * This needs to be run manually. It will not run as part of the standard unit tests as it is named "Tester...". This
 * could be changed once the bug has been fixed.
 */
public class TesterFileStoreConcurrency {

    private static final int TEST_RUN_TIME_MS = 5000;

    private static final FileStore FILE_STORE = new FileStore();
    private static final String STORE_DIR = "STORE_TMP";
    private static final File STORE_FILE = new File(STORE_DIR);

    private static final int SESSION_COUNT = 10;
    private static final Session[] SESSIONS = new Session[SESSION_COUNT];


    @BeforeClass
    public static void setup() {
        Context context = new TesterContext();
        Manager manager = new TesterManager();
        manager.setContext(context);
        for (int i = 0; i < SESSION_COUNT; i++) {
            SESSIONS[i] = new StandardSession(null);
            SESSIONS[i].setManager(manager);
            SESSIONS[i].setId(Integer.toString(i));
        }

        FILE_STORE.setDirectory(STORE_FILE.getAbsolutePath());
        FILE_STORE.setManager(manager);
    }


    @AfterClass
    public static void cleanUp() {
        ExpandWar.delete(STORE_FILE);
    }


    @Test
    public void testConcurrency() throws Exception {
        SaveTask saveTask = new SaveTask();
        Thread saveThread = new Thread(saveTask);
        saveThread.start();

        LoadTask loadTask = new LoadTask();
        Thread loadThread = new Thread(loadTask);
        loadThread.start();

        RemoveTask removeTask = new RemoveTask();
        Thread removeThread = new Thread(removeTask);
        removeThread.start();

        Thread.sleep(TEST_RUN_TIME_MS);

        saveTask.stop();
        loadTask.stop();
        removeTask.stop();

        saveThread.join();
        loadThread.join();
        removeThread.join();

        Assert.assertFalse("Exception during save", saveTask.getFailed());
        Assert.assertFalse("Exception during load", loadTask.getFailed());
        Assert.assertFalse("Exception during remove", removeTask.getFailed());

        System.out.println("Looped over sessions [" + saveTask.getLoopCount() + "] times calling save()");
        System.out.println("Looped over sessions [" + loadTask.getLoopCount() + "] times calling load()");
        System.out.println("Looped over sessions [" + removeTask.getLoopCount() + "] times calling remove()");
    }


    private static final class SaveTask extends TaskBase {

        @Override
        protected void doTask(Session session) throws Exception {
            FILE_STORE.save(session);
        }

        @Override
        protected String getTaskName() {
            return "save";
        }
    }


    private static final class LoadTask extends TaskBase {

        @Override
        protected void doTask(Session session) throws Exception {
            FILE_STORE.load(session.getId());
        }

        @Override
        protected String getTaskName() {
            return "load";
        }
    }


    private static final class RemoveTask extends TaskBase {

        @Override
        protected void doTask(Session session) throws Exception {
            FILE_STORE.remove(session.getId());
        }

        @Override
        protected String getTaskName() {
            return "remove";
        }
    }


    private abstract static class TaskBase implements Runnable {

        private volatile boolean stop = false;
        private volatile boolean failed = false;
        private volatile int loopCount = 0;

        @Override
        public void run() {
            while (!stop) {
                for (Session session : SESSIONS) {
                    try {
                        doTask(session);
                    } catch (Exception e) {
                        System.out.println("Failed to " + getTaskName() + " session [" + session.getId() + "]");
                        e.printStackTrace(System.out);
                        stop = true;
                        failed = true;
                    }
                }
                loopCount++;
            }
        }

        public void stop() {
            stop = true;
        }

        public boolean getFailed() {
            return failed;
        }

        public int getLoopCount() {
            return loopCount;
        }

        protected abstract void doTask(Session session) throws Exception;
        protected abstract String getTaskName();
    }
}
