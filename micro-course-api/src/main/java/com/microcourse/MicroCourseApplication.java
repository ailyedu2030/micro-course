package com.microcourse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MicroCourseApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroCourseApplication.class, args);
    }
}
