package com.grid07.service;

import com.grid07.dto.CreateCommentRequest;
import com.grid07.dto.CreatePostRequest;
import com.grid07.dto.LikeRequest;
import com.grid07.model.Bot;
import com.grid07.model.Comment;
import com.grid07.model.Post;
import com.grid07.repository.BotRepository;
import com.grid07.repository.CommentRepository;
import com.grid07.repository.PostRepository;
import com.grid07.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostService {

    private final PostRepository      postRepo;
    private final CommentRepository   commentRepo;
    private final UserRepository      userRepo;
    private final BotRepository       botRepo;
    private final GuardrailService    guardrail;
    private final ViralityService     virality;
    private final NotificationService notification;

    public PostService(PostRepository postRepo,
                       CommentRepository commentRepo,
                       UserRepository userRepo,
                       BotRepository botRepo,
                       GuardrailService guardrail,
                       ViralityService virality,
                       NotificationService notification) {
        this.postRepo     = postRepo;
        this.commentRepo  = commentRepo;
        this.userRepo     = userRepo;
        this.botRepo      = botRepo;
        this.guardrail    = guardrail;
        this.virality     = virality;
        this.notification = notification;
    }

    // ── Create Post ───────────────────────────────────────────────────────────

    public Post createPost(CreatePostRequest req) {
        validateAuthor(req.getAuthorId(), req.getAuthorType());

        Post post = new Post();
        post.setAuthorId(req.getAuthorId());
        post.setAuthorType(req.getAuthorType().toUpperCase());
        post.setContent(req.getContent());
        return postRepo.save(post);
    }

    // ── Add Comment ───────────────────────────────────────────────────────────

    public Comment addComment(Long postId, CreateCommentRequest req) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Post not found: " + postId));

        String authorType = req.getAuthorType().toUpperCase();

        if ("BOT".equals(authorType)) {
            applyBotGuardrails(postId, post, req);
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(req.getAuthorId());
        comment.setAuthorType(authorType);
        comment.setContent(req.getContent());
        comment.setDepthLevel(req.getDepthLevel());
        Comment saved = commentRepo.save(comment);

        // Update virality score in Redis
        if ("BOT".equals(authorType)) {
            virality.incrementBotReply(postId);
            // Notify post owner if they are a human user
            if ("USER".equals(post.getAuthorType())) {
                Bot bot = botRepo.findById(req.getAuthorId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Bot not found: " + req.getAuthorId()));
                notification.handleBotInteraction(
                        post.getAuthorId(),
                        bot.getName(),
                        bot.getName() + " replied to your post"
                );
            }
        } else {
            virality.incrementHumanComment(postId);
        }

        return saved;
    }

    private void applyBotGuardrails(Long postId, Post post, CreateCommentRequest req) {
        // 1. Vertical Cap: reject if depth > 20 (no Redis, cheapest check first)
        if (!guardrail.checkVerticalCap(req.getDepthLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thread depth exceeds maximum of 20 levels.");
        }

        // 2. Cooldown Cap: check BEFORE incrementing bot count
        // so we don't waste a bot_count slot if bot is still in cooldown
        if ("USER".equals(post.getAuthorType())) {
            Long humanId = post.getAuthorId();
            if (!guardrail.checkAndSetCooldown(req.getAuthorId(), humanId)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Bot is in cooldown for this user. Try again after 10 minutes.");
            }
        }

        // 3. Horizontal Cap: atomically increment LAST
        // only reaches here if all other checks passed
        if (!guardrail.checkAndIncrementBotCount(postId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Post has reached the maximum of 100 bot replies.");
        }
    }

    // ── Like Post ─────────────────────────────────────────────────────────────

    public void likePost(Long postId, LikeRequest req) {
        postRepo.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Post not found: " + postId));

        // Human Like = +20 virality points
        virality.incrementHumanLike(postId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateAuthor(Long authorId, String authorType) {
        if (authorType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authorType must be USER or BOT");
        }
        switch (authorType.toUpperCase()) {
            case "USER" -> userRepo.findById(authorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found: " + authorId));
            case "BOT"  -> botRepo.findById(authorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Bot not found: " + authorId));
            default     -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authorType must be USER or BOT");
        }
    }
}