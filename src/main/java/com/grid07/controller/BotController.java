package com.grid07.controller;

import com.grid07.model.Bot;
import com.grid07.repository.BotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotRepository botRepo;

    public BotController(BotRepository botRepo) {
        this.botRepo = botRepo;
    }

    @PostMapping
    public ResponseEntity<Bot> createBot(@RequestBody Bot bot) {
        return ResponseEntity.status(HttpStatus.CREATED).body(botRepo.save(bot));
    }

    @GetMapping
    public ResponseEntity<List<Bot>> getAllBots() {
        return ResponseEntity.ok(botRepo.findAll());
    }
}
