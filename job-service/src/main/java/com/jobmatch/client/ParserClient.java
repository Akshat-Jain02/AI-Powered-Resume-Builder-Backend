package com.jobmatch.client;

import com.jobmatch.config.FeignConfig;
import com.jobmatch.dto.TextRequest;
import com.jobmatch.model.ParsedResume;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
    name = "section-service",
    configuration = FeignConfig.class
)
public interface ParserClient {

    @PostMapping(value = "/parser/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String extractText(@RequestPart("file") MultipartFile file);

    @PostMapping(value = "/parser/analyze-text",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ParsedResume analyzeText(@RequestBody TextRequest textRequest);

    @PostMapping(value = "/parser/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ParsedResume analyzeResume(@RequestPart("file") MultipartFile file);
}
