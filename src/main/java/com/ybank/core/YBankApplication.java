package com.ybank.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class YBankApplication {
  public static void main(String[] args) { SpringApplication.run(YBankApplication.class, args); }
}
