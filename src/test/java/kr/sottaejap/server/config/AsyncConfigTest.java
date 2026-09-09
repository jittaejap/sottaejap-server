package kr.sottaejap.server.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 제안 이유 executor의 거절 정책 (PR #46 리뷰 1).
 *
 * <p>기본 정책({@code AbortPolicy})이면 거절이 {@code TaskRejectedException}으로 <b>호출 스레드</b>에 올라온다.
 * {@code @Async} 프록시가 submit하는 자리가 곧 {@code RetrospectServiceImpl.save}라, 회고가 커밋되고 이름까지
 * 지은 뒤에 {@code POST /retrospects}가 500으로 끝난다. 여기서 확인하는 것은 그 예외가 나지 않는다는 것 하나다.
 */
class AsyncConfigTest {

    /** corePoolSize 2 · maxPoolSize 4 · queueCapacity 50 — 스레드 4 + 큐 50이면 가득 찬다. */
    private static final int CAPACITY = 54;

    private ThreadPoolTaskExecutor executor;
    private CountDownLatch gate;

    @BeforeEach
    void setUp() {
        executor = (ThreadPoolTaskExecutor) new AsyncConfig().suggestionReasonExecutor(
                new ThreadPoolTaskExecutorBuilder());
        executor.initialize();
        gate = new CountDownLatch(1);
    }

    @AfterEach
    void tearDown() {
        gate.countDown();
        executor.shutdown();
    }

    @Test
    void 큐가_차도_거절이_예외로_올라오지_않는다() {
        for (int i = 0; i < CAPACITY; i++) {
            executor.execute(this::blockUntilReleased);
        }

        assertDoesNotThrow(() -> executor.execute(this::blockUntilReleased));
    }

    @Test
    void 종료_중에_들어온_작업도_예외로_올라오지_않는다() {
        // 배포 중 재시작이 시작된 뒤 들어온 저장이 밟는 경로다.
        gate.countDown();
        executor.shutdown();

        assertDoesNotThrow(() -> executor.execute(() -> {
        }));
    }

    private void blockUntilReleased() {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
