package com.grid07.dto;

import lombok.Data;

@Data
public class CreatePostRequest {
    private Long authorId;
    // "USER" or "BOT"
    private String authorType;
    private String content;
}
