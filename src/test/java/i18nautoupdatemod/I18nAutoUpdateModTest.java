package i18nautoupdatemod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nAutoUpdateModTest {
    @AfterEach
    void resetUpdater() {
        I18nAutoUpdateMod.resetUpdateForTests();
    }

    @Test
    void startsOnceAndReturnsWithoutWaitingForWork() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blockingUpdate = () -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        long startNanos = System.nanoTime();
        CompletableFuture<Void> first =
                I18nAutoUpdateMod.startAsyncOnce(blockingUpdate);
        long elapsedMillis =
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        CompletableFuture<Void> second =
                I18nAutoUpdateMod.startAsyncOnce(() -> {
                    throw new AssertionError("duplicate update");
                });

        assertTrue(elapsedMillis < 500, "startAsyncOnce blocked for " + elapsedMillis + " ms");
        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertSame(first, second);
        release.countDown();
        first.get(2, TimeUnit.SECONDS);
    }
}
