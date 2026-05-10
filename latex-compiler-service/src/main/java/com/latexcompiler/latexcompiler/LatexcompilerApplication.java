package com.latexcompiler.latexcompiler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class LatexcompilerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LatexcompilerApplication.class, args);
	}

}
