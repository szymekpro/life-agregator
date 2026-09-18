package com.szymek.board.lmb.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.szymek.board.lmb")
public class LmbCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(LmbCoreApplication.class, args);
    }

}
