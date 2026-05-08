# Social API — Spring Boot Assignment (Grid07)

## What I Built

This is a Spring Boot backend for a social media platform where both humans and AI bots can create posts and comments. The main challenge was making sure bots don't go crazy and spam everything — so I built a guardrail system using Redis to control that.

---

## Tech Stack

- Java 17
- Spring Boot 3.5
- PostgreSQL — stores all the actual data (users, bots, posts, comments)
- Redis — acts as the gatekeeper for all bot interactions
- Docker — used to run Redis locally

---

## How to Run This Project

**Step 1 — Start Redis and Postgres using Docker:**
```bash
docker-compose up -d
```

**Step 2 — Update application.properties with your Postgres password**

**Step 3 — Run the Spring Boot app from your IDE or:**
```bash
./mvnw spring-boot:run
```

App runs on `http://localhost:8080`

---

## API Endpoints

| Method | Endpoint | What it does |
|--------|----------|--------------|
| POST | /api/users | Create a user |
| POST | /api/bots | Create a bot |
| POST | /api/posts | Create a post |
| POST | /api/posts/{postId}/comments | Add a comment |
| POST | /api/posts/{postId}/like | Like a post |

---

## Project Structure

```
src/main/java/com/grid07/
├── controller/      → REST endpoints
├── model/           → JPA entities (User, Bot, Post, Comment)
├── repository/      → Database queries
├── service/         → Business logic
│   ├── PostService.java
│   ├── GuardrailService.java
│   ├── ViralityService.java
│   └── NotificationService.java
├── scheduler/       → CRON job for notifications
├── dto/             → Request body classes
└── config/          → Redis configuration
```

---

## Phase 2 — How I Handled Thread Safety (The Important Part)

This was the trickiest part of the assignment. The requirement was that even if 200 bots hit the same post at the exact same millisecond, the bot reply count should stop at exactly 100 — not 101, not 102.

### The Problem with Normal Approach

If I just did this in Java:
```java
int count = redis.get("post:1:bot_count");  // Thread A reads 99
// Thread B also reads 99 at the same time!
if (count < 100) {
    redis.increment("post:1:bot_count");     // Both A and B increment → becomes 101!
}
```
This would fail because two threads can read the same value before either of them increments it. This is called a **race condition**.

### My Solution — Lua Script

I used a **Lua script executed atomically inside Redis**. Redis runs Lua scripts as a single atomic operation — meaning no other command can run in between.

```lua
local current = redis.call('GET', KEYS[1])
if current == false then
  redis.call('SET', KEYS[1], 1)
  return 1
elseif tonumber(current) < tonumber(ARGV[1]) then
  return redis.call('INCR', KEYS[1])
else
  return -1
end
```

The script does the GET + CHECK + INCR in one atomic step inside Redis. So even if 200 threads call this at the same time, Redis processes them one by one. The 101st request gets -1 back and is rejected with 429 Too Many Requests.

This is how I guaranteed the horizontal cap holds exactly at 100 under any concurrent load.

### Other Guardrails

**Cooldown Cap (10 min per bot-human pair):**
Used Redis `SETNX` (Set if Not Exists) with a 10-minute TTL:
```java
redis.opsForValue().setIfAbsent("cooldown:bot_1:human_1", "1", Duration.ofMinutes(10));
```
If key exists → bot is blocked. If not → key is created and interaction is allowed.

**Vertical Cap (max depth 20):**
Simple check on the `depthLevel` field in the request. No Redis needed here.

### Order of Checks (Important!)

I check guardrails in this order:
1. Vertical cap (cheapest, no Redis)
2. Cooldown cap (Redis SETNX)
3. Horizontal cap (Redis Lua script — most expensive, runs last)

This way if a bot fails the cooldown check, we never waste a bot_count slot by incrementing it unnecessarily.

---

## Phase 3 — Notification System

When a bot interacts with a user's post:
- If user hasn't been notified in last 15 min → send immediately + set 15 min cooldown key
- If user was already notified recently → push to a Redis List (`user:{id}:pending_notifs`)

A `@Scheduled` CRON job runs every 5 minutes, scans all pending notification lists, pops all messages, logs a summarized notification like:
```
Summarized Push Notification: BotAlpha and [3] others interacted with your posts.
```

---

## Statelessness

Everything is stored in Redis — no HashMaps, no static variables anywhere in the Java code. The Spring Boot app is completely stateless and could run on multiple instances without any issues.

---

## Sample Postman Requests

**Create User:**
```json
POST /api/users
{
  "username": "chaitanya",
  "isPremium": true
}
```

**Add Bot Comment:**
```json
POST /api/posts/1/comments
{
  "authorId": 1,
  "authorType": "BOT",
  "content": "Nice post!",
  "depthLevel": 1
}
```
