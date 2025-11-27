package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.submit(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Object expected = new Object();
        Future<Object> f = s.submit(r, expected);
        doSleep(10);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
    }

    @Test
    void testScheduleCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("X", 10);
        Future<String> f = s.submit(c);
        doSleep(20);
        assertTrue(f.isDone());
        assertEquals("X", f.get());
    }

    @Test
    void testShutdown() {
        ExecutorService s = MyExecService.newInstance();
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertThrows(
            RejectedExecutionException.class,
            () -> s.submit(new TestRunnable()));
    }

    static void doSleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testIsShutdownAndIsTerminated() throws Exception {
        MyExecService s = MyExecService.newInstance();

        assertFalse(s.isShutdown());
        assertFalse(s.isTerminated());

        s.shutdown();
        assertTrue(s.isShutdown());

        s.awaitTermination(1, TimeUnit.SECONDS);

        assertTrue(s.isTerminated());
    }

    @Test
    void testShutdownNow() {
        MyExecService s = MyExecService.newInstance();

        TestRunnable r1 = new TestRunnable();
        TestRunnable r2 = new TestRunnable();

        s.execute(r1);
        s.execute(r2);

        var notExecuted = s.shutdownNow();

        assertNotNull(notExecuted);

        assertThrows(
                RejectedExecutionException.class,
                () -> s.submit(new TestRunnable())
        );
    }

    @Test
    void testAwaitTerminationWithTask() throws Exception {
        MyExecService s = MyExecService.newInstance();

        s.execute(() -> ExecServiceTest.doSleep(50));

        s.shutdown();

        boolean finished = s.awaitTermination(1, TimeUnit.SECONDS);

        assertTrue(finished);
        assertTrue(s.isTerminated());
    }

    @Test
    void testInvokeAll() throws Exception {
        MyExecService s = MyExecService.newInstance();

        StringCallable c1 = new StringCallable("A", 10);
        StringCallable c2 = new StringCallable("B", 20);
        StringCallable c3 = new StringCallable("C", 5);

        var tasks = java.util.List.of(c1, c2, c3);

        var futures = s.invokeAll(tasks);

        assertEquals(3, futures.size());

        for (Future<String> f : futures) {
            assertTrue(f.isDone());
        }

        assertEquals("A", futures.get(0).get());
        assertEquals("B", futures.get(1).get());
        assertEquals("C", futures.get(2).get());
    }

    @Test
    void testInvokeAllWithTimeout() throws Exception {
        MyExecService s = MyExecService.newInstance();

        StringCallable c1 = new StringCallable("FAST", 10);
        StringCallable c2 = new StringCallable("SLOW1", 200);
        StringCallable c3 = new StringCallable("SLOW2", 200);

        var tasks = java.util.List.of(c1, c2, c3);

        var futures = s.invokeAll(tasks, 50, TimeUnit.MILLISECONDS);

        assertEquals(3, futures.size());

        assertTrue(futures.get(0).isDone());

    }

    @Test
    void testInvokeAny() throws Exception {
        MyExecService s = MyExecService.newInstance();

        StringCallable fast = new StringCallable("WINNER", 5);
        StringCallable slow = new StringCallable("LOSER", 50);

        var tasks = java.util.List.of(fast, slow);

        String result = s.invokeAny(tasks);

        assertEquals("WINNER", result);
    }

    @Test
    void testInvokeAnyAllFail() {
        MyExecService s = MyExecService.newInstance();

        Callable<String> bad1 = () -> { throw new RuntimeException("BAD1"); };
        Callable<String> bad2 = () -> { throw new RuntimeException("BAD2"); };

        var tasks = java.util.List.of(bad1, bad2);

        assertThrows(ExecutionException.class, () -> s.invokeAny(tasks));
    }

    @Test
    void testInvokeAnyWithTimeout() {
        MyExecService s = MyExecService.newInstance();

        StringCallable slow = new StringCallable("TOO SLOW", 200);

        var tasks = java.util.List.of(slow);

        assertThrows(
                TimeoutException.class,
                () -> s.invokeAny(tasks, 50, TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testSubmitRunnableReturnsDoneFuture() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();

        Future<?> f = s.submit(r);

        doSleep(10);

        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertNull(f.get());
    }








}

class StringCallable implements Callable<String> {

    private final String result;
    private final int milis;

    StringCallable(String result, int milis) {
        this.result = result;
        this.milis = milis;
    }

    @Override
    public String call() throws Exception {
        ExecServiceTest.doSleep(milis);
        return result;
    }
}
class TestRunnable implements Runnable {

    boolean wasRun;
    @Override
    public void run() {
        wasRun = true;
    }
}
