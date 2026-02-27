---
description: Add a new backend API endpoint with OpenAPI documentation
---

# Add Backend Endpoint

## 1. Define the DTO

Create request/response DTO(s) in `BES/src/main/java/com/example/BES/dtos/`:
- Use `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor` (Lombok)
- Keep DTOs flat — avoid exposing JPA entity relationships directly

## 2. Add Service Method

In the appropriate service class under `services/`:
- Inject the repository via constructor
- Handle business logic and error cases
- Return DTO, not entity

## 3. Add Controller Endpoint

In the appropriate controller under `controllers/`:
- Follow URL pattern: `/api/v1/{resource}`
- Add OpenAPI annotations:
  ```java
  @Operation(summary = "Description of what this does")
  @ApiResponse(responseCode = "200", description = "Success response description")
  ```
- Use `@GetMapping`, `@PostMapping`, `@DeleteMapping` etc.

## 4. Write Test

Create/update test in `BES/src/test/java/com/example/BES/`:
```java
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MyControllerTest {
    @Autowired MockMvc mockMvc;
    // ...
}
```

## 5. Verify

```bash
cd BES && ./mvnw test
```

Check Swagger UI at `http://localhost:5050/swagger-ui.html` to verify the API docs.
