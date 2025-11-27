package uj.wmii.pwj.exec;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.Deque;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.ArrayList;



public class MyExecService implements ExecutorService {
    private final Thread worker;
    private final Deque<Runnable> taskQueue = new ArrayDeque<>();
    private final Object lock = new Object();

    private volatile boolean shutdown = false;
    private volatile boolean terminated = false;

    private MyExecService() {
        this.worker = new Thread(this::workerLoop, "MyExecService-Worker");
        this.worker.start();
    }

    private void workerLoop() {
        try {
            while (true) {
                Runnable task;
                synchronized (lock) {
                    while (taskQueue.isEmpty() && !shutdown) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    if (taskQueue.isEmpty() && shutdown) {
                        break;
                    }
                    task = taskQueue.removeFirst();
                }
                try {
                    task.run();
                } catch (Throwable t) {
                }
            }
        } finally {
            terminated = true;
        }
    }



    static MyExecService newInstance() {
        return new MyExecService();
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            shutdown = true;
            lock.notifyAll();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> notExecuted;
        synchronized (lock) {
            shutdown = true;
            notExecuted = new ArrayList<>(taskQueue);
            taskQueue.clear();
            lock.notifyAll();
        }
        worker.interrupt();
        return notExecuted;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long millis = unit.toMillis(timeout);
        worker.join(millis);
        return !worker.isAlive();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task);
        CompletableFuture<T> cf = new CompletableFuture<>();
        execute(() -> {
            if (cf.isCancelled()) {
                return;
            }
            try {
                T result = task.call();
                cf.complete(result);
            } catch (Throwable ex) {
                cf.completeExceptionally(ex);
            }
        });

        return cf;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task);
        CompletableFuture<T> cf = new CompletableFuture<>();
        execute(() -> {
            if (cf.isCancelled()) {
                return;
            }
            try {
                task.run();
                cf.complete(result);
            } catch (Throwable ex) {
                cf.completeExceptionally(ex);
            }
        });

        return cf;
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(task, null);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Objects.requireNonNull(tasks);
        List<Future<T>> futures = new ArrayList<>(tasks.size());

        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }

        for (Future<T> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {

            }
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(tasks);
        long nanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + nanos;

        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }

        for (Future<T> f : futures) {
            if (nanos <= 0L) {
                break;
            }
            try {
                f.get(nanos, TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {

            } catch (TimeoutException e) {
                break;
            }
            nanos = deadline - System.nanoTime();
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        Objects.requireNonNull(tasks);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks is empty");
        }

        ExecutionException last = null;

        for (Callable<T> task : tasks) {
            Future<T> f = submit(task);
            try {
                return f.get();
            } catch (ExecutionException e) {
                last = e;
            }
        }

        if (last != null) {
            throw last;
        }
        throw new ExecutionException(new Exception("No task completed successfully"));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(tasks);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks is empty");
        }

        long nanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + nanos;
        ExecutionException last = null;

        for (Callable<T> task : tasks) {
            if (nanos <= 0L) {
                throw new TimeoutException("Timeout before any task completed");
            }
            Future<T> f = submit(task);
            try {
                return f.get(nanos, TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                last = e;
            } catch (TimeoutException e) {
                throw e;
            }
            nanos = deadline - System.nanoTime();
        }

        if (last != null) {
            throw last;
        }
        throw new ExecutionException(new Exception("No task completed successfully"));
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command);
        synchronized (lock) {
            if (shutdown) {
                throw new RejectedExecutionException("Executor already shutdown");
            }
            taskQueue.addLast(command);
            lock.notifyAll();
        }
    }
}
