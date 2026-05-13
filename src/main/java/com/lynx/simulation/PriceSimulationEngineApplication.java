package com.lynx.simulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PriceSimulationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceSimulationEngineApplication.class, args);
    }

}
