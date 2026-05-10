package com.resumeai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.resumeai.config.FeignConfig;

@FeignClient(
 name = "section-service",
 configuration = FeignConfig.class
)
public interface ParserClient {

	@PostMapping(value = "/parser/extract-text",consumes = "multipart/form-data")
	public String extractText(@RequestPart("file") MultipartFile multipartFile);
}
