package org.apache.catalina.users;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class TestMemoryUserDatabaseConcurrency {

    @Test
    public void testConcurrentSave() throws Exception {
        MemoryUserDatabase db = new MemoryUserDatabase("TestDB");
        db.setPathname("test-users.xml");
        db.setReadonly(false);
        db.init();

        // Create some data
        db.createRole("role1", "description1");
        db.createUser("user1", "pass1", "User One");

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    db.save();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("Success count: " + successCount.get());
        System.out.println("Error count: " + errorCount.get());

        Assert.assertEquals("Some saves failed due to race condition", numThreads, successCount.get());
        
        File file = new File("test-users.xml");
        file.delete();
        new File("test-users.xml.new").delete();
        new File("test-users.xml.old").delete();
    }
}
