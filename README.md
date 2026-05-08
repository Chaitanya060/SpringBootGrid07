# SocialAPI — Spring Boot Microservice

A high-performance Spring Boot 3.x microservice implementing a social-post backend with Redis-backed virality scoring, atomic concurrency guardrails, and a smart notification batching engine.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL 15 |
| Cache / State | Redis 7 (Spring Data Redis) |
| Containerisation | Docker Compose |

---

## Quick Start

```bash
# 1. Start Postgres + Redis
docker-compose up -d

# 2. Run the application
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/posts` | Create a post (User or Bot author) |
| POST | `/api/posts/{postId}/comments` | Add a comment; bot guardrails enforced |
| POST | `/api/posts/{postId}/like` | Like a post (+20 virality) |

### Sample requests

**Create a post**
```json
POST /api/posts
{
  "authorId": 1,
  "authorType": "USER",
  "content": "Hello world!"
}
```

**Add a bot comment**
```json
POST /api/posts/1/comments
{
  "authorId": 1,
  "authorType": "BOT",
  "content": "Great post!",
  "depthLevel": 1
}
```

**Like a post**
```json
POST /api/posts/1/like
{
  "userId": 1
}
```

---

## Phase 2 — Thread Safety: How Atomic Locks Work

### The Race-Condition Problem

Without atomicity, two concurrent bot requests can both read `bot_count = 99`, both decide the cap hasn't been reached, and both increment to `100` — resulting in `101` comments in the database.

### The Solution: Redis Lua Script

The **Horizontal Cap** (max 100 bot replies per post) is enforced by a single Lua script executed atomically inside Redis:

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

Redis executes Lua scripts as a **single, indivisible operation** — no other command can interleave. This means:

- Thread A reads 99, increments → 100 ✅
- Thread B arrives at the same nanosecond, reads 100 (already set by A), returns -1 → **rejected** ✅

The Spring application calls this script via `StringRedisTemplate.execute(RedisScript<Long>, ...)`.

### Cooldown Cap

Uses `SET key 1 EX 600 NX` (via `setIfAbsent` with a TTL). The NX flag ensures only one thread can "win" the write — subsequent calls return `false` and are rejected.

### Vertical Cap

A pure in-memory check: `depthLevel <= 20`. Redis is not needed because the depth value is supplied by the caller and never changes for a given request.

### Guardrail Order (fail-fast)

1. **Vertical Cap** — cheapest check, no Redis round-trip
2. **Horizontal Cap** — atomic Redis Lua script (also increments the counter)
3. **Cooldown Cap** — atomic Redis SET NX

The database write only happens **after all three guardrails pass**, ensuring no phantom rows appear in PostgreSQL when a guardrail rejects the request.

---

## Phase 3 — Notification Engine

- **Throttler**: First bot interaction within a 15-minute window logs `"Push Notification Sent to User"` and sets a 15-min TTL key. Subsequent interactions during that window are queued in a Redis List (`user:{id}:pending_notifs`).
- **CRON Sweeper**: `@Scheduled(fixedRate = 300_000)` (every 5 min) scans all pending lists, pops messages atomically, and logs a summarised message: `"Summarized Push Notification: Bot X and [N] others interacted with your posts."`

---

## Statelessness

All mutable state lives exclusively in Redis:

| State | Redis Key |
|---|---|
| Virality score | `post:{id}:virality_score` |
| Bot reply count | `post:{id}:bot_count` |
| Bot-human cooldown | `cooldown:bot_{botId}:human_{humanId}` |
| Notification cooldown | `notif_cooldown:user:{userId}` |
| Pending notifications | `user:{userId}:pending_notifs` |

No `HashMap`, no `static` fields, no in-process caches.
