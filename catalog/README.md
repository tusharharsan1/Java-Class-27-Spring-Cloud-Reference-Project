# productcatalogservice (ShopNest): the project at the END of Class 25

The catalog with Redis: the product details are cached with `@Cacheable` (and deleted when the product changes, with random seconds on the TTL), a Redis that fails never breaks the shop, and each caller of the search by meaning has a limit of 5 a minute. The numbers of a search sentence (from Google Gemini) are also cached, by hand, with a lock for each sentence: **that is the homework of the class**, and this project has the finished answer. Spring Boot 4.1.1, Java 17 (also runs on 21), MySQL 8 or newer, Redis 8 (any recent Redis or Valkey), port **8080**. The search by meaning needs `GEMINI_API_KEY` (a free key from Google AI Studio) and the numbers of all products (the one-time job `--search.embed-on-start=true`, about 75 seconds).

The folder `given/` has the plumbing, and `given/typed/` has every piece that is typed in class, in the order of the class.

## What Class 25 changed

| File | How it is made in class |
|---|---|
| `pom.xml`, `application.properties` | Given: `bash given/apply.sh connect` adds the two starters (`spring-boot-starter-data-redis`, `spring-boot-starter-cache`) and the Redis lines: host, port, **timeouts of 200 ms**, `spring.cache.type=redis`, a TTL of 10 minutes, the key prefix `shopnest:`. |
| `services/ProductReader` | Typed: one method with `@Cacheable(cacheNames = "product", key = "#id")` that gives back the DTO. A class of its own, because a `@Cacheable` method that is called from the same class is not cached. |
| `services/StorageProductService` | Typed: `@CacheEvict(cacheNames = "product", key = "#id")` above `replaceProduct`, `updateProduct` and `deleteProduct`. |
| `controllers/ProductController` | `getProductById` asks the reader (pasted). |
| `config/CacheConfig` | Given (`bash given/apply.sh cache`): `@EnableCaching`, and the cache `product` with JSON values, the prefix and a TTL of 10 minutes plus 0 to 59 random seconds (so that keys saved together do not expire together). Later typed: `implements CachingConfigurer` and an error handler, so that a failing Redis is logged and the database answers. |
| `services/QueryVectorStore` | **Homework.** Given (`bash given/apply.sh store`): every Redis call of the search (`get`, `save` for 24 hours, `tryLock`, `unlock`, `waitFor`), each one fail-open. |
| `services/MeaningSearchService` | **Homework.** Typed: the method `vectorOf`: the key is the sentence in small letters, look, ask Gemini, save the numbers (never when Gemini fails: the plain search answers and nothing is saved). Then the lock with `SET NX EX 10` and a `finally`. |
| `services/RateLimiter`, `controllers/SearchController` | Given (`bash given/apply.sh limiter`): `INCR`, `EXPIRE` on the first call, the limit of 5 in a minute, fail-open. The caller is `user:<sub>` with a login and `ip:<address>` without one. 429 with `Retry-After`. |
| `given/fill-keys.sh`, `given/fill-memory.sh` | The demos of a slow command (`KEYS`) and of a full memory (`noeviction` against `allkeys-lru`). |

The Groq pitch of the earlier classes is not changed.

## Run it

```bash
export DB_PASSWORD=your-password GEMINI_API_KEY=your-free-key GROQ_API_KEY=any-value
mvn spring-boot:run 2>&1 | tee server.log        # needs MySQL, Redis on 6379, and the user service on 8081 for the login token
```

## What was tested (JDK 21, Boot 4.1.1, Redis 8.10.2, MySQL 8.4.11, the real Gemini embedding service)

- `mvn clean verify` with a test schema and a Redis on localhost:6379: see the numbers in the last lines of this file. The Redis test is skipped when no Redis answers.
- **The class was rebuilt from the code boxes of the script**, starting from the catalog of Class 22 (`given/apply.sh connect`, the typed pieces, `apply.sh cache`, `limiter`), and every command of the script was run as written. The homework (`apply.sh store`, `vectorOf`, the lock) was built the same way. The result is the same as this project.
- 100 reads of one product: **200 SQL statements** without the cache, **2** with it. The key is `shopnest:product::<id>`, the value is readable JSON, the TTL is 600 plus 0 to 59 random seconds (five keys saved together had 5 different TTLs).
- 20 reads of one product at the same moment on an empty cache (a stampede on MySQL): **8 to 40 SQL statements**, and not 2 (about 40 on the first run after a restart, then 8 to 14).
- A search by meaning ("something to make my eyes look bigger") with the real Gemini: **0.74 and 0.50 seconds** without the cache. With the cache: **0.89 seconds** the first time after a restart, then **0.08 seconds**. The key is `shopnest:query:<the sentence>`, the TTL is 86400, the value is about **9.7 KB** (768 numbers as text). The 0.08 seconds that remain are loading the 194 products and the cosine. The answer is the same (Essence Mascara Lash Princess 0.66, Eyeshadow Palette with Mirror 0.65, Red Lipstick 0.61).
- 20 identical searches at the same moment on a new sentence: **20 calls to Gemini** without a lock, **1** with it. Five different sentences together: 0.64 seconds and 5 calls. Spring's `@Cacheable(sync = true)` did **not** stop a stampede (tested with 50 requests on an earlier version). With a locking cache writer it did, but the lock was for the whole cache, so five keys took 2, 4, 6, 8 and 10 seconds one after the other.
- The free Gemini key answered **429 "Too many requests"** after 100 texts in a minute (the one-time job of the search class).
- Redis stopped: the product read is a **500** until the error handler is added, then a 200. The search answers in about 0.65 seconds (Gemini is asked every time). `/actuator/health` says DOWN while the shop works. Redis hanging for 6 seconds (`CLIENT PAUSE`): **0.57 seconds** with the 200 ms timeout, **5.8 seconds** with the default.
- The limit: five answers of 200, then **429** with `Retry-After` (59 in our run). A second user and a caller without a login have their own counters.
- At the `redis-cli` prompt: 1,000 parallel `INCR` give 1,000, and 1,000 parallel read-and-write give about 200. `KEYS *` on 1.5 million keys took 117 ms in the slow log, and a `PING` that came in the middle waited about 55 ms. With `maxmemory 5mb` and `noeviction`, 5,000 of 8,000 writes were refused (`OOM command not allowed`); with `allkeys-lru` none was.
- A finding about tests: the cache saves and deletes on a connection of its own, a moment after the method returns. A test that counts calls must wait for the key (`ProductReaderCacheTest` does).
- **Not tested:** Redis on Windows (WSL2), Valkey.
- `mvn clean verify`: **107 tests, 0 failures, 89 percent of the instructions covered** (JaCoCo). `ProductReaderCacheTest` also checks that keys saved together get different TTLs.
