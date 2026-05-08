package com.grid07.controller;

import com.grid07.dto.CreateCommentRequest;
import com.grid07.dto.CreatePostRequest;
import com.grid07.dto.LikeRequest;
import com.grid07.model.Comment;
import com.grid07.model.Post;
import com.grid07.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * POST /api/posts
     * Create a new post authored by a User or a Bot.
     *
     * Request body:
     * {
     *   "authorId": 1,
     *   "authorType": "USER",   // or "BOT"
     *   "content": "Hello world!"
     * }
     */
    @PostMapping("/posts")
    public ResponseEntity<Post> createPost(@RequestBody CreatePostRequest request) {
        Post post = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    /**
     * POST /api/posts/{postId}/comments
     * Add a comment to a post. If the author is a BOT, all guardrails are enforced.
     *
     * Request body:
     * {
     *   "authorId": 2,
     *   "authorType": "BOT",
     *   "content": "Interesting take!",
     *   "depthLevel": 1
     * }
     *
     * Returns 429 Too Many Requests when a guardrail blocks the bot.
     */
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Comment> addComment(
            @PathVariable Long postId,
            @RequestBody CreateCommentRequest request) {
        Comment comment = postService.addComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    /**
     * POST /api/posts/{postId}/like
     * Like a post (human interaction). Adds +20 virality points.
     *
     * Request body:
     * {
     *   "userId": 1
     * }
     */
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<String> likePost(
            @PathVariable Long postId,
            @RequestBody LikeRequest request) {
        postService.likePost(postId, request);
        return ResponseEntity.ok("Post liked successfully.");
    }
}
