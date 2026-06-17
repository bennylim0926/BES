package com.example.BES;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BesApplication {

	public static void main(String[] args){
		SpringApplication.run(BesApplication.class, args);
	}
}
