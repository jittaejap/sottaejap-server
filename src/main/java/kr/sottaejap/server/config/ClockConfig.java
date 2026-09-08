package kr.sottaejap.server.config;

import kr.sottaejap.server.common.enums.TimeSlot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * "오늘"은 서비스 계층만 본다. 규칙 엔진(rules/)은 현재시각을 참조하지 않는다 (E-18 · NFR-01).
 * 테스트는 Clock.fixed로 바꿔 끼운다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(TimeSlot.ZONE);
    }
}
