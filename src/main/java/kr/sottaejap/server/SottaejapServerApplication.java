package kr.sottaejap.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// 매일 회고 요청 알림을 만든다 (FR-10-01). 사용자가 앱을 열지 않아도 Web Push가 나가야 하기 때문이다.
@EnableScheduling
public class SottaejapServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SottaejapServerApplication.class, args);
    }
}
