# Module: Testing

Migrate test infrastructure from Spring Boot Test to Quarkus Test.

Load [references/annotation-map.md](../references/annotation-map.md) — see the Testing section for full mapping.

## What to do

- [ ] Replace `@SpringBootTest` with `@QuarkusTest`
- [ ] Replace `@MockBean` with `@InjectMock` (`io.quarkus.test.InjectMock`)
- [ ] Replace `TestRestTemplate` with REST Assured
- [ ] Replace `@ActiveProfiles("test")` with `@TestProfile`
- [ ] Replace `@LocalServerPort` with `@TestHTTPResource`
- [ ] Update test properties (use `%test.` prefix in `application.properties`)
- [ ] Run tests: `./mvnw test` (Maven) or `./gradlew test` (Gradle)

## Key Conversions

```java
// BEFORE: Spring Boot Test
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class TodoControllerTest {
    @Autowired TestRestTemplate restTemplate;
    @MockBean TodoService todoService;

    @Test
    void shouldListTodos() {
        when(todoService.findAll()).thenReturn(List.of(new Todo("Test")));
        ResponseEntity<String> response = restTemplate.getForEntity("/todos", String.class);
        assertEquals(200, response.getStatusCode().value());
    }
}

// AFTER: Quarkus Test
@QuarkusTest
public class TodoResourceTest {
    @InjectMock TodoService todoService;

    @Test
    void shouldListTodos() {
        when(todoService.findAll()).thenReturn(List.of(new Todo("Test")));
        given()
            .when().get("/todos")
            .then().statusCode(200);
    }
}
```

## Dependencies

Ensure these are in the build file (should already be added by the build module):

**Maven:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5-mockito</artifactId>
    <scope>test</scope>
</dependency>
```

**Gradle:**
```groovy
testImplementation 'io.quarkus:quarkus-junit5'
testImplementation 'io.rest-assured:rest-assured'
testImplementation 'io.quarkus:quarkus-junit5-mockito'
```

## Watch out

- **`@InjectMock` package**: Use `io.quarkus.test.InjectMock` (since Quarkus 3.2). The old `io.quarkus.test.junit.mockito.InjectMock` is removed in 4.0.
- **Test port**: Quarkus tests default to port 8081. If your app uses 8081, set `quarkus.http.test-port=0`.
- **No `@WebMvcTest` equivalent**: Use `@QuarkusTest` for all test types. For data-only tests, use `@QuarkusTest` with a test profile that disables unneeded extensions.