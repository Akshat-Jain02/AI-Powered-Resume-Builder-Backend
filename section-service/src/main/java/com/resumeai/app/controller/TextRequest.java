package com.resumeai.app.controller;

import jakarta.validation.constraints.NotBlank;

public class TextRequest {

    @NotBlank(message = "Text cannot be blank")
    private String text;

    public TextRequest() {}

    public TextRequest(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}