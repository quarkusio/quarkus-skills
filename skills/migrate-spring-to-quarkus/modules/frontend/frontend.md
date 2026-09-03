# Module: Frontend / View Layer

Migrate templates, static assets, and view-related code from Spring MVC + Thymeleaf to Quarkus + Qute.

## What to do

- [ ] Ensure `quarkus-rest-qute` dependency is in the build file
- [ ] Convert Thymeleaf templates to Qute syntax
- [ ] Move static resources from `static/` to `META-INF/resources/`
- [ ] Remove Spring CSRF tokens from HTML and JavaScript
- [ ] Rename template directories to match `@CheckedTemplate` class names
- [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

## Dependency

Use `quarkus-rest-qute` — **never** `quarkus-qute` alone:

**Maven:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-qute</artifactId>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.quarkus:quarkus-rest-qute'
```

`quarkus-qute` is the standalone engine without REST integration. It will fail at runtime when JAX-RS resources return `TemplateInstance`. `quarkus-rest-qute` includes Qute and adds the REST integration layer.

## Thymeleaf → Qute Syntax Conversion

| Thymeleaf | Qute | Notes |
|---|---|---|
| `th:text="${name}"` | `{name}` | Direct expression |
| `th:utext="${html}"` | `{html.raw}` | Unescaped HTML output |
| `th:each="item : ${items}"` | `{#for item in items}...{/for}` | Loop |
| `th:if="${condition}"` | `{#if condition}...{/if}` | Conditional |
| `th:unless="${condition}"` | `{#if !condition}...{/if}` | Negated conditional |
| `th:href="@{/path/{id}(id=${item.id})}"` | `href="/path/{item.id}"` | URL with path param |
| `th:action="@{/submit}"` | `action="/submit"` | Form action |
| `th:value="${value}"` | `value="{value}"` | Input value |
| `th:class="${active ? 'on' : 'off'}"` | `class="{active ? 'on' : 'off'}"` | Conditional class |
| `th:fragment="name"` | `{#include name /}` | Template fragment/include |

## Template File Location

When using `@CheckedTemplate`, template files must match the enclosing class name:

```
templates/todos.html                    → templates/TodoResource/todos.html
templates/todo-detail.html              → templates/TodoResource/todoDetail.html
```

## Qute Strict Data Map (Critical)

Unlike Thymeleaf (which silently treats missing variables as null), Qute throws `TemplateException` if a template key is missing from the data map. **Every `.data()` call site must provide the same complete set of keys.** For example, if the template references `tasks`, `noTasks`, `totalPages`:

- The **empty-result** path must include: `.data("tasks", List.of()).data("noTasks", true).data("totalPages", 0)`
- The **has-results** path must include: `.data("noTasks", false)` in addition to actual data

Start migration with `quarkus.qute.strict-rendering=false` and `quarkus.qute.property-not-found-strategy=output-original`, fix all missing variables, then enable strict mode.

## Static Assets

```
# BEFORE (Spring Boot)
src/main/resources/static/css/style.css
src/main/resources/static/js/app.js

# AFTER (Quarkus)
src/main/resources/META-INF/resources/css/style.css
src/main/resources/META-INF/resources/js/app.js
```

## CSRF Token Removal

Quarkus does not use Spring Security's CSRF mechanism. Remove these from templates and JavaScript:

```html
<!-- DELETE from HTML: -->
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
```

```javascript
// DELETE from JS:
const token = document.querySelector('meta[name="_csrf"]').content;
const header = document.querySelector('meta[name="_csrf_header"]').content;
headers[header] = token;
```

If the app needs CSRF protection in Quarkus, use `quarkus-csrf-reactive`.