package com.openclaw.manager.openclawserversmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenclawServersManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenclawServersManagerApplication.class, args);
    }

}
