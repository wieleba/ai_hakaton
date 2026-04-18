# Java 21 Spring Boot Project

## Build & Run

- **Build**: `./gradlew build`
- **Dev**: `./gradlew bootRun`
- **Tests**: `./gradlew test`
- **Format**: `./gradlew spotlessApply`

## Project Structure

Feature-based flat architecture (each feature is self-contained, no subfolders):

```
src/main/java/
  features/
    users/              # User feature - flat package
      UserController.java
      UserService.java
      UserRepository.java
      User.java         # JPA entity
    products/           # Product feature - flat package
      ProductController.java
      ProductService.java
      ProductRepository.java
      Product.java
    orders/             # Order feature - flat package
      OrderController.java
      OrderService.java
      OrderRepository.java
      Order.java
  shared/               # Shared utilities, exceptions
    exception/
    util/
src/test/java/
  features/
    users/
      UserControllerTest.java
      UserServiceTest.java
    products/
      ProductControllerTest.java
    orders/
      OrderControllerTest.java
src/main/resources/     # application.properties, templates, static files
build.gradle            # Build configuration and dependencies
gradle/                 # Gradle wrapper and scripts
```

## Key Technologies

- Spring Boot 3.x (latest)
- Java 21 (LTS)
- Gradle
- JUnit 5, Mockito for testing
- Spring Data JPA
- Spring Web MVC
- Spring WebSocket for real-time communication
- PostgreSQL 15+ (database)
- Flyway (database migrations)

## Code Style

- Indentation: 4 spaces
- Format: Google Java Style (enforced by Spotless via Gradle)
- Line length: 120 characters
- **Visibility**: Package-private by default, public only when necessary
- **DTOs**: Use records instead of classes, define as inner classes of controllers
- **Tests**: Place in `src/test/java/` same package structure, no need for public visibility

## Testing

- Write unit tests for business logic
- Integration tests use testcontainers with real PostgreSQL (not H2)
- Test database configured with `ddl-auto=validate` and Flyway migrations
- Use `@SpringBootTest` for integration tests
- Mockito for dependencies

## Common Commands

```bash
./gradlew build            # Full build
./gradlew bootRun          # Start dev server
./gradlew test             # Run all tests
./gradlew test --tests ClassName  # Run specific test
./gradlew spotlessApply    # Auto-format
./gradlew clean            # Clean build
```

## WebSocket Communication

- Uses Spring WebSocket for real-time bidirectional messaging
- STOMP protocol for message routing
- Message broker for pub/sub patterns
- WebSocket endpoints typically at `/ws`
- SimpMessagingTemplate for sending messages to clients
- @MessageMapping for handling incoming messages
- @SendTo for broadcasting to subscribed clients

## Database (PostgreSQL)

**Local Setup:**
- PostgreSQL 15+ (or use Docker: `docker run -d --name postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15`)
- Create database: `createdb app_db` or via psql

**Configuration (application.properties):**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/app_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.flyway.enabled=true
```

**Flyway Migrations:**
- Place SQL files in `src/main/resources/db/migration/`
- Naming convention: `V1__initial_schema.sql`, `V2__add_users_table.sql`
- Flyway runs migrations automatically on application startup
- Always use version numbers; undo migrations not supported

**Environment Variables (Production):**
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Never commit credentials to git

## Architecture Notes

- **Package by Feature**: Each feature is a flat package under `features/` with no subfolders
- **Visibility**: Use package-private by default, public only when crossing feature boundaries
- **DTOs**: Define as inner records within controllers, never separate classes or files
- **Test Structure**: Tests in `src/test/java/` same package as code, can access package-private classes
- **Records**: Use Java records for all DTOs - concise, immutable, generated equals/hashCode/toString
- Services should be stateless
- Repositories abstract data access
- WebSocket handlers are stateful per connection
- Use ThreadLocal carefully in WebSocket contexts
- Shared code only in `shared/` module
- Each feature owns its entities and business logic
- Cross-feature communication through service interfaces or events
