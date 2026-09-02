package kr.sottaejap.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SottaejapServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SottaejapServerApplication.class, args);
    }
}
