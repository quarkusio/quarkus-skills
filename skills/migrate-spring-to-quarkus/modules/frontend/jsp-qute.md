# Module: Frontend / View Layer - JSP to Qute

Migrate JSP templates, JSTL tags, static assets, and view-related code from Java EE/Spring MVC + JSP to Quarkus + Qute.

## What to do

* [ ] Ensure `quarkus-rest-qute` dependency is in the build file
* [ ] Convert JSP files (`.jsp`) to Qute templates (`.html`)
* [ ] Replace JSP EL expressions and JSTL tags with Qute syntax
* [ ] Replace Spring form tags with standard HTML and Qute expressions
* [ ] Remove JSP directives (`<%@ page %>`, `<%@ taglib %>`)
* [ ] Convert JSP includes and custom tags to Qute includes or user tags
* [ ] Move static resources from `webapp/` to `META-INF/resources/`
* [ ] Remove JSP implicit objects and servlet API usage from templates
* [ ] Remove Spring/Java EE CSRF tokens from HTML and JavaScript
* [ ] Rename template directories to match `@CheckedTemplate` class names
* [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

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

## JSP → Qute Syntax Conversion

### Basic Expressions & Output

| JSP                                          | Qute                           | Notes                      |
| -------------------------------------------- | ------------------------------ | -------------------------- |
| `${name}`                                    | `{name}`                       | Direct expression          |
| `<c:out value="${name}"/>`                   | `{name}`                       | Auto-escaped output        |
| `<c:out value="${html}" escapeXml="false"/>` | `{html.raw}`                   | Unescaped HTML output      |
| `${user.name}`                               | `{user.name}`                  | Property access            |
| `${items[0]}`                                | `{items[0]}`                   | List/array access          |
| `${map['key']}`                              | `{map['key']}`                 | Map access                 |
| `${empty items}`                             | `{#if items.isEmpty}...{/if}`  | Empty collection check     |
| `${not empty items}`                         | `{#if !items.isEmpty}...{/if}` | Non-empty collection check |
| `${foo ? 'yes' : 'no'}`                      | `{foo ? 'yes' : 'no'}`         | Ternary expression         |

### JSP Directives (Remove)

| JSP                                    | Qute                  | Notes             |
| -------------------------------------- | --------------------- | ----------------- |
| `<%@ page contentType="text/html" %>`  | Remove                | Handled by JAX-RS |
| `<%@ page session="false" %>`          | Remove                | Not applicable    |
| `<%@ taglib prefix="c" uri="..." %>`   | Remove                | No JSTL imports   |
| `<%@ taglib prefix="fmt" uri="..." %>` | Remove                | No JSTL imports   |
| `<%@ taglib prefix="fn" uri="..." %>`  | Remove                | No JSTL imports   |
| `<%@ include file="header.jsp" %>`     | `{#include header /}` | Template include  |

### Control Flow (JSTL Core)

| JSP (JSTL)                                                                            | Qute                            | Notes                  |
| ------------------------------------------------------------------------------------- | ------------------------------- | ---------------------- |
| `<c:if test="${condition}">...</c:if>`                                                | `{#if condition}...{/if}`       | Conditional            |
| `<c:if test="${!condition}">...</c:if>`                                               | `{#if !condition}...{/if}`      | Negated conditional    |
| `<c:choose><c:when test="${x}">...</c:when><c:otherwise>...</c:otherwise></c:choose>` | `{#if x}...{#else}...{/if}`     | Conditional branches   |
| `<c:forEach items="${items}" var="item">...</c:forEach>`                              | `{#for item in items}...{/for}` | Loop                   |
| `<c:forEach items="${items}" var="item" varStatus="status">`                          | `{#for item in items}...{/for}` | Use Qute loop metadata |
| `<c:set var="x" value="${y}"/>`                                                       | `{#let x=y}...{/let}`           | Local variable         |
| `<c:remove var="x"/>`                                                                 | N/A                             | Not needed             |

### URL & Links

| JSP                                                               | Qute                   | Notes                             |
| ----------------------------------------------------------------- | ---------------------- | --------------------------------- |
| `<c:url value="/path/${id}"/>`                                    | `href="/path/{id}"`    | URL with path parameter           |
| `<c:url value="/path"><c:param name="id" value="${id}"/></c:url>` | `href="/path?id={id}"` | Query parameter                   |
| `${pageContext.request.contextPath}/path`                         | `/path`                | Context path usually not required |

### Includes & Reusable Components

| JSP                                | Qute                                | Notes                     |
| ---------------------------------- | ----------------------------------- | ------------------------- |
| `<jsp:include page="header.jsp"/>` | `{#include header /}`               | Include template          |
| `<%@ include file="header.jsp" %>` | `{#include header /}`               | Static include            |
| Custom tag `<my:tag>`              | Qute user tag or `{#include ... /}` | Depends on implementation |

### Form Elements

| JSP                                     | Qute                                  | Notes          |
| --------------------------------------- | ------------------------------------- | -------------- |
| `<input value="${user.name}"/>`         | `<input value="{user.name}"/>`        | Input value    |
| `<textarea>${user.bio}</textarea>`      | `<textarea>{user.bio}</textarea>`     | Textarea value |
| `<input ${checked ? 'checked' : ''}/>`  | `<input {#if checked}checked{/if}/>`  | Checkbox       |
| `<input ${selected ? 'checked' : ''}/>` | `<input {#if selected}checked{/if}/>` | Radio button   |

### Scriptlets & Declarations (Remove)

| JSP                  | Qute                        | Notes             |
| -------------------- | --------------------------- | ----------------- |
| `<% Java code %>`    | Move to Java resource class | No scriptlets     |
| `<%! declaration %>` | Move to Java class          | No declarations   |
| `<%= expression %>`  | `{expression}`              | Expression output |

## Spring Form Tag Conversion

Replace Spring form tags with standard HTML and Qute expressions.

**Before:**

```jsp
<form:form modelAttribute="user">
    <form:input path="name"/>
</form:form>
```

**After:**

```html
<form method="post">
    <input name="name" value="{user.name}">
</form>
```

## JSP Implicit Objects (Critical)

JSP implicit objects are not available in Qute templates.

| JSP Object                   | Replacement                               |
| ---------------------------- | ----------------------------------------- |
| `request`                    | Pass required values from resource        |
| `response`                   | Handle in JAX-RS resource                 |
| `session`                    | Retrieve in resource and pass to template |
| `application`                | CDI bean or configuration                 |
| `pageContext`                | No equivalent                             |
| `${param.id}`                | Pass as template data                     |
| `${header['User-Agent']}`    | Pass as template data                     |
| `${cookie.sessionId.value}`  | Pass as template data                     |
| `${sessionScope.user}`       | Pass as template data                     |
| `${applicationScope.config}` | Inject in Java and pass as template data  |

Do not access servlet infrastructure directly from templates. Pass only the data required for rendering.

## JSTL Formatting and Functions

JSTL formatting and function tags do not have universal Qute equivalents.

Move formatting, string manipulation, and display logic into Java before rendering.

**Before:**

```jsp
<fmt:formatDate value="${createdAt}" pattern="yyyy-MM-dd"/>
<fmt:formatNumber value="${amount}" pattern="#,##0.00"/>
${fn:substring(name, 0, 5)}
```

**After:**

```java
template
    .data("createdAtFormatted",
        createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE))
    .data("amountFormatted",
        decimalFormat.format(amount))
    .data("shortName",
        name.substring(0, 5));
```

```html
{createdAtFormatted}
{amountFormatted}
{shortName}
```

### Internationalization (i18n)

JSTL:

```jsp
<fmt:message key="label.welcome"/>
```

Qute:

```html
{msg:welcome}
```

Requires the `quarkus-qute-i18n` extension.

## Template File Location

**Before (JSP):**

```text
src/main/webapp/WEB-INF/views/todos.jsp
src/main/webapp/WEB-INF/views/todoDetail.jsp
```

**After (Qute):**

```text
src/main/resources/templates/TodoResource/todos.html
src/main/resources/templates/TodoResource/todoDetail.html
```

When using `@CheckedTemplate`, template files must match the enclosing class name and method names.

## Qute Strict Data Map (Critical)

Unlike JSP EL (which silently treats missing variables as null), Qute throws `TemplateException` if a template key is missing from the data map.

Every `.data()` call site must provide the same complete set of keys.

If a template references:

```html
{#if noTasks}
    No tasks found
{/if}

{#for task in tasks}
    ...
{/for}
```

then every render path must provide both variables:

```java
Templates.todos()
    .data("tasks", List.of())
    .data("noTasks", true);
```

and

```java
Templates.todos()
    .data("tasks", tasks)
    .data("noTasks", false);
```

Start migration with:

```properties
quarkus.qute.strict-rendering=false
quarkus.qute.property-not-found-strategy=output-original
```

Fix all missing variables, then enable strict rendering.

## Static Assets

```text
# BEFORE (Java EE / Spring MVC)
src/main/webapp/css/style.css
src/main/webapp/js/app.js
src/main/webapp/images/logo.png

# AFTER (Quarkus)
src/main/resources/META-INF/resources/css/style.css
src/main/resources/META-INF/resources/js/app.js
src/main/resources/META-INF/resources/images/logo.png
```

## CSRF Token Removal

Quarkus does not use Spring Security's CSRF mechanism. Remove these from templates and JavaScript:

```html
<!-- DELETE from HTML -->
<meta name="_csrf" content="${_csrf.token}"/>
<meta name="_csrf_header" content="${_csrf.headerName}"/>
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
```

```javascript
// DELETE from JS
const token = document.querySelector('meta[name="_csrf"]').content;
const header = document.querySelector('meta[name="_csrf_header"]').content;
headers[header] = token;
```

If the application requires CSRF protection in Quarkus, use `quarkus-csrf-reactive`.

## Common Pitfalls

1. Missing template variables cause runtime failures in Qute strict mode.
2. Remove all `${pageContext.request.contextPath}` usage.
3. Delete all `<%@ taglib %>` directives.
4. Move scriptlets and declarations into Java classes.
5. Rename `.jsp` files to `.html`.
6. Move templates from `WEB-INF/views` to `resources/templates`.
7. Convert custom tags to Qute user tags, includes, or Java-side logic.
8. Precompute formatting and string manipulation in Java rather than recreating JSTL helper behavior in templates.
