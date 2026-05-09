package es.triana.company.wealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(WealthApplication.class, args);
    }
}
