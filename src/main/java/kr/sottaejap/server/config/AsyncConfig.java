package kr.sottaejap.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 응답 경로 밖에서 도는 AI 후속 작업용 스레드 풀.
 *
 * <p>제안 이유({@code SuggestionReasonService})는 회고 저장 응답에 실리지 않으므로 사용자를 기다리게 하지
 * 않는다. 동기로 두면 저장 1건의 왕복이 명명 15초 + 이유 15초로 최악 30초가 되어, 클라이언트 공통 타임아웃
 * 20초가 먼저 끊는다 (PR #46 리뷰 2).
 *
 * <p>기본 executor를 쓰지 않고 전용 빈을 둔다 — 이 작업이 느려도 다른 비동기 작업이 밀리지 않게 하고,
 * 큐 길이를 여기 한곳에서 본다.
 *
 * <p><b>{@code Executor} 빈을 두면 Boot의 {@code applicationTaskExecutor} 자동 구성이 물러난다</b>
 * ({@code spring.task.execution.mode=auto} 기본). 지금은 쓰는 곳이 없다 — {@code @Async}는 제안 이유 한
 * 곳뿐이고 {@code @Scheduled}는 별도 {@code taskScheduler}를 쓴다. 나중에 qualifier 없는 {@code @Async}나
 * MVC 비동기 응답을 붙이면 {@code SimpleAsyncTaskExecutor}로 떨어지므로 그때 이 자리를 본다 (PR #46 리뷰 2).
 *
 * <p>ponytail: 인스턴스가 하나이고 데모 규모라는 전제다 (07 §1). 처리량이 문제가 되면 여기 값을 올린다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public TaskExecutor suggestionReasonExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(2)
                .maxPoolSize(4)
                .queueCapacity(50)
                .threadNamePrefix("suggestion-reason-")
                // 큐가 찼거나 종료 중이면 버린다. 기본 정책(AbortPolicy)은 TaskRejectedException을 호출
                // 스레드로 올리는데, @Async 프록시가 submit하는 자리가 곧 RetrospectServiceImpl.save라
                // 회고가 커밋되고 이름까지 지은 뒤에 POST /retrospects가 500으로 끝난다 (PR #46 리뷰 1).
                // 이유가 비면 화면이 템플릿으로 채우므로(E-38) 버리는 편이 맞다. 추적하려고 로그는 남긴다.
                .customizers(executor -> executor.setRejectedExecutionHandler((task, pool) ->
                        log.warn("제안 이유 작업을 버렸습니다. 큐가 찼거나 종료 중입니다. queue={} shutdown={}",
                                pool.getQueue().size(), pool.isShutdown())))
                .build();
    }
}
