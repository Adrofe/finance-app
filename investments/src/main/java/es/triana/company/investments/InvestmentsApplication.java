package es.triana.company.investments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InvestmentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestmentsApplication.class, args);
    }
}
