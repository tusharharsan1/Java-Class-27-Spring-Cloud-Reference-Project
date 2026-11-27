# Class 27 · Spring Cloud Service Discovery

ShopNest Catalog calls a local embedding service. The lab starts with a fixed URL and finishes by looking up the instance in Eureka. The fixture returns deterministic numbers in the same JSON shape as the earlier Gemini client.

## Start here

Use Java 17+, Maven, MySQL 8, Bash, curl and Python 3. Mac or Linux work as JVM processes. For Windows, use WSL2 and keep Java and MySQL on the same side. Use three terminals for the apps and one for commands. Run the commands from the repository root.

```bash
git switch --detach start
mvn -q -f eurekaserver/pom.xml -DskipTests package
mvn -q -f embeddingservice/pom.xml -DskipTests package
mvn -q -f catalog/pom.xml -DskipTests package
```

In REGISTRY:

```bash
java -jar eurekaserver/target/eurekaserver-0.0.1-SNAPSHOT.jar
```

In EMBEDDING:

```bash
java -jar embeddingservice/target/embeddingservice-0.0.1-SNAPSHOT.jar
```

In CATALOG, set your local MySQL password before sharing your screen. Start Catalog with the classroom profile:

```bash
java -jar catalog/target/productcatalogservice-0.0.1-SNAPSHOT.jar --spring.profiles.active=class27
```

On the first run, Flyway creates the tables in the separate schema `shopnest_c27`. In LAPTOP, seed only this schema. This seed adds three sample products and can be run again:

```bash
mysql -h 127.0.0.1 -u root -p shopnest_c27 < catalog/seed/class27-products.sql
bash catalog/given/search.sh
```

Expect HTTP 200, Red Lipstick, and a numeric score. The classroom profile uses the fixture directly and leaves Redis, Gemini and UserAuth out of this public search demo. Eureka's dashboard is at `http://127.0.0.1:8761/`.

## Follow the class

- Stop the fixture in EMBEDDING and start it with the same command plus `--server.port=9091`.
- The fixed Catalog URL still uses port 9090. Search again and read the keyword fallback. The product has a null score.
- Open `catalog/src/main/java/com/scaler/productcatalogservice/clients/EmbeddingClient.java`. Replace only `embeddingBase()` using the lecture. The copy is in `catalog/given/typed/01-embedding-base.txt`.
- Rebuild and restart Catalog. Search again. The Catalog log prints the discovered port and the answer has a score.
- `main` is the finished code. To return to the opening demo from a ZIP of `main`, run `java -jar catalog/target/productcatalogservice-0.0.1-SNAPSHOT.jar --spring.profiles.active=class27 --embedding.discover=false` to force the fixed address. For the typing demo, copy the file `catalog/given/EmbeddingClient.start.txt` over `EmbeddingClient.java` before building.
- Stop your processes with Ctrl+C when you finish. Keep the fast leases and standalone registry settings on a laptop. They are classroom settings.

## Tests

Use a throwaway catalog test schema and a local Redis on port 6379 for the entire suite. Set `DB_PASSWORD`, `GROQ_API_KEY` (a dummy value is enough), and `SPRING_DATASOURCE_URL` for the test schema. Run `mvn -f catalog/pom.xml test` from `main`. The client test verifies the changing address, the outbound wire format, and the fallback exception when discovery returns no candidate.
