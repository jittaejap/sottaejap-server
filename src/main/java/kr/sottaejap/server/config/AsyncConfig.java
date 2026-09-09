package kr.sottaejap.server.config;

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
 * <p>ponytail: 인스턴스가 하나이고 데모 규모라는 전제다 (07 §1). 큐가 차면
 * {@code ThreadPoolTaskExecutor} 기본 정책대로 호출 스레드에서 돌지 않고 거절되며, 그때는 이유가 비어
 * 화면이 템플릿으로 뜬다 — 회고 저장 자체는 이미 커밋돼 있어 영향이 없다. 처리량이 문제가 되면 여기 값을 올린다.
 */
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
                .build();
    }
}
