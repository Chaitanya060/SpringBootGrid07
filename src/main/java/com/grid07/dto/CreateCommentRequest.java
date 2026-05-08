package com.grid07.dto;

import lombok.Data;

@Data
public class CreateCommentRequest {
    private Long authorId;
    // "USER" or "BOT"
    private String authorType;
    private String content;
    // Thread depth (0 = top-level, >0 = nested reply)
    private int depthLevel;
}
