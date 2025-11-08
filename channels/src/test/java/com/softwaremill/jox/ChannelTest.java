package com.softwaremill.jox;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import static com.softwaremill.jox.TestUtil.forkVoid;
import static com.softwaremill.jox.TestUtil.scoped;
import static org.junit.jupiter.api.Assertions.*;

/** Channel tests which are run for various capacities. */
public class ChannelTest {
    @TestWithCapacities
    void testSendReceiveInManyForks(int capacity) throws ExecutionException, InterruptedException {
        // given
        Channel<Integer> channel =
                capacity == 0
                        ? Channel.newRendezvousChannel()
                        : Channel.newBufferedChannel(capacity);
        var fs = new HashSet<Future<Void>>();
        var s = new ConcurrentSkipListSet<Integer>();

        // when
        scoped(
                scope -> {
                    for (int i = 1; i <= 1000; i++) {
                        int ii = i;
                        forkVoid(scope, () -> channel.send(ii));
                    }
                    for (int i = 1; i <= 1000; i++) {
                        fs.add(forkVoid(scope, () -> s.add(channel.receive())));
                    }
                    for (Future<Void> f : fs) {
                        f.get();
                    }

                    // then
                    assertEquals(1000, s.size());
                });
    }

    @TestWithCapacities
    void testSendReceiveManyElementsInTwoForks(int capacity)
            throws ExecutionException, InterruptedException {
        // given
        Channel<Integer> channel =
                capacity == 0
                        ? Channel.newRendezvousChannel()
                        : Channel.newBufferedChannel(capacity);
        var s = new ConcurrentSkipListSet<Integer>();

        // when
        scoped(
                scope -> {
                    forkVoid(
                            scope,
                            () -> {
                                for (int i = 1; i <= 1000; i++) {
                                    channel.send(i);
                                }
                            });
                    forkVoid(
                                    scope,
                                    () -> {
                                        for (int i = 1; i <= 1000; i++) {
                                            s.add(channel.receive());
                                        }
                                    })
                            .get();

                    // then
                    assertEquals(1000, s.size());
                });
    }

    @Test
    void testNullItem() throws InterruptedException {
        Channel<Object> ch = Channel.newBufferedChannel(4);
        assertThrows(NullPointerException.class, () -> ch.send(null));
    }

    /// mem 4096kB
    /// total 75_457ms
    @Test  @Disabled
    void benchmarkChannel () throws InterruptedException {
        var item = "same";
        var ch = Channel.newBufferedChannel(1_000_000_000);
        assertEquals(1_000_000_000, ch.getCapacity());
        System.out.println("0) created");

        long used0 = gc();
        long t = System.nanoTime();

        System.out.println("1) send");
        for (int i = 0; i < ch.getCapacity(); i++)
            ch.send(item);
        System.out.println("send benchmark: " + (System.nanoTime() - t)/1000/1000.0);
        System.out.println("memory: " + (gc() - used0)/1024.0);

        System.out.println("2) receive");
        for (int i = 0; i < ch.getCapacity(); i++)
            assertSame(item, ch.receive());
        System.out.println("send+receive benchmark: " + (System.nanoTime() - t)/1000/1000.0);
    }

    /// memory 901.23kB
    /// total 30_087ms
    @Test  @Disabled
    void benchmarkABQ () throws InterruptedException {
        var item = "same";
        int max = 1_000_000_000;
        var ch = new ArrayBlockingQueue<>(max);
        assertEquals(max, ch.remainingCapacity());
        System.out.println("0) created");

        long used0 = gc();
        long t = System.nanoTime();

        System.out.println("1) send");
        for (int i = 0; i < max; i++)
            ch.put(item);
        System.out.println("send benchmark: " + (System.nanoTime() - t)/1000/1000.0);
        System.out.println("memory: " + (gc() - used0)/1024.0);

        System.out.println("2) receive");
        for (int i = 0; i < max; i++)
            assertSame(item, ch.take());
        System.out.println("send+receive benchmark: " + (System.nanoTime() - t)/1000/1000.0);
    }

    /// memory OOM kB
    /// total > 3mi
    @Test  @Disabled
    void benchmarkLBQ () throws InterruptedException {
        var item = "same";
        int max = 1_000_000_000;
        var ch = new LinkedBlockingDeque<>(max);
        assertEquals(max, ch.remainingCapacity());
        System.out.println("0) created");

        long used0 = gc();
        long t = System.nanoTime();

        System.out.println("1) send");
        for (int i = 0; i < max; i++)
            ch.put(item);
        System.out.println("send benchmark: " + (System.nanoTime() - t)/1000/1000.0);
        System.out.println("memory: " + (gc() - used0)/1024.0);

        System.out.println("2) receive");
        for (int i = 0; i < max; i++)
            assertSame(item, ch.take());
        System.out.println("send+receive benchmark: " + (System.nanoTime() - t)/1000/1000.0);
    }

    public static long gc () {
        Runtime runtime = Runtime.getRuntime();
//        for (int i = 0; i < 3; i++){
//            runtime.gc();
//            Thread.yield();
//        }
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
