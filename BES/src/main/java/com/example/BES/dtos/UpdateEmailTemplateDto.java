package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateEmailTemplateDto {
    @NotBlank @Size(max = 500)
    private String subject;
    @NotBlank
    private String body;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
