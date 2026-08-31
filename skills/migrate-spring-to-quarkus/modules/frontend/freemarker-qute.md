# Module: Frontend / View Layer - FreeMarker to Qute

Migrate FreeMarker templates, macros, static assets, and view-related code from Spring MVC + FreeMarker to Quarkus + Qute.

## What to do

* [ ] Ensure `quarkus-rest-qute` dependency is in the build file
* [ ] Convert FreeMarker templates (`.ftl`) to Qute templates (`.html`)
* [ ] Replace FreeMarker directives with Qute sections
* [ ] Replace FreeMarker expressions (`${}`) with Qute expressions (`{}`)
* [ ] Convert FreeMarker macros and imports to Qute user tags or includes
* [ ] Move static resources to `META-INF/resources/`
* [ ] Remove Spring Security CSRF tokens from HTML and JavaScript
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

## FreeMarker → Qute Syntax Conversion

### Basic Expressions & Output

| FreeMarker         | Qute                                    | Notes                   |
| ------------------ | --------------------------------------- | ----------------------- |
| `${name}`          | `{name}`                                | Expression output       |
| `${user.name}`     | `{user.name}`                           | Property access         |
| `${items[0]}`      | `{items[0]}`                            | List access             |
| `${map.key}`       | `{map.key}`                             | Map/property access     |
| `${html?no_esc}`   | `{html.raw}`                            | Unescaped HTML          |
| `${foo!"default"}` | Provide value in Java                   | Avoid template defaults |
| `${foo??}`         | `{#if foo??}` or explicit null handling | Existence check         |

### Conditionals

| FreeMarker                    | Qute                             | Notes             |
| ----------------------------- | -------------------------------- | ----------------- |
| `<#if condition>...</#if>`    | `{#if condition}...{/if}`        | Conditional       |
| `<#if a>...<#else>...</#if>`  | `{#if a}...{#else}...{/if}`      | If/else           |
| `<#if a><#elseif b>...</#if>` | `{#if a}...{#else if b}...{/if}` | Multiple branches |
| `<#if items?has_content>`     | `{#if !items.isEmpty}`           | Collection check  |

### Loops

| FreeMarker              | Qute                   | Notes                  |
| ----------------------- | ---------------------- | ---------------------- |
| `<#list items as item>` | `{#for item in items}` | Loop                   |
| `${item_index}`         | Loop metadata          | Use Qute loop metadata |
| `${item_has_next}`      | Loop metadata          | Use Qute loop metadata |
| `<#break>`              | Refactor in Java       | No direct equivalent   |
| `<#continue>`           | Refactor in Java       | No direct equivalent   |

### Variables

| FreeMarker            | Qute                      | Notes                       |
| --------------------- | ------------------------- | --------------------------- |
| `<#assign x = value>` | `{#let x=value}...{/let}` | Local variable              |
| `<#local x = value>`  | `{#let x=value}...{/let}` | Local scope                 |
| `<#global x = value>` | Move to Java              | Avoid global template state |

### Includes, Imports & Macros

| FreeMarker                         | Qute                  | Notes                   |
| ---------------------------------- | --------------------- | ----------------------- |
| `<#include "header.ftl">`          | `{#include header /}` | Include template        |
| `<#import "layout.ftl" as layout>` | Qute user tag         | Convert macro libraries |
| `<@layout.header />`               | User tag invocation   | Convert macro usage     |
| `<#macro table rows>`              | Qute user tag         | Reusable component      |

## Macros and User Tags

FreeMarker macros do not have a direct equivalent.

Convert reusable macros into:

* Qute user tags (`templates/tags/`)
* Qute includes (`{#include ... /}`)
* Java helper methods

**Before (FreeMarker):**

```ftl
<#macro alert message type>
<div class="alert alert-${type}">
    ${message}
</div>
</#macro>
```

**After (Qute User Tag):**

```html
{#alert message=message type=type /}
```

## Built-ins and Formatting

Many FreeMarker built-ins do not have direct Qute equivalents.

Move formatting and transformation logic into Java before rendering.

| FreeMarker                     | Recommended Qute Migration |
| ------------------------------ | -------------------------- |
| `${date?string("yyyy-MM-dd")}` | Format in Java             |
| `${amount?string.currency}`    | Format in Java             |
| `${name?upper_case}`           | Transform in Java          |
| `${name?lower_case}`           | Transform in Java          |
| `${text?substring(0,5)}`       | Transform in Java          |
| `${list?size}`                 | `{list.size}`              |

**Before:**

```ftl
${createdAt?string("yyyy-MM-dd")}
${amount?string.currency}
${name?upper_case}
```

**After:**

```java
template
    .data("createdAtFormatted",
        createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE))
    .data("amountFormatted",
        currencyFormat.format(amount))
    .data("nameUpper",
        name.toUpperCase());
```

```html
{createdAtFormatted}
{amountFormatted}
{nameUpper}
```

## Spring FreeMarker Macros

Spring MVC applications often use Spring macro libraries:

```ftl
<#import "/spring.ftl" as spring>

<@spring.message "welcome.message"/>
```

Convert to Qute i18n:

```html
{msg:welcome.message}
```

Requires `quarkus-qute-i18n`.

## Template File Location

**Before (Spring MVC + FreeMarker):**

```text
src/main/resources/templates/todos.ftl
src/main/resources/templates/todoDetail.ftl
```

**After (Qute):**

```text
src/main/resources/templates/TodoResource/todos.html
src/main/resources/templates/TodoResource/todoDetail.html
```

When using `@CheckedTemplate`, template files must match the enclosing class name and method names.

## Qute Strict Data Map (Critical)

Unlike FreeMarker, which often tolerates missing values through default operators (`!`) and existence checks (`??`), Qute throws `TemplateException` when referenced template data is missing.

Every `.data()` call site must provide the complete set of keys referenced by the template.

For example, if a template references:

```html
{#if noTasks}
    No tasks found
{/if}

{#for task in tasks}
    ...
{/for}
```

Every render path must provide both variables:

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
# BEFORE (Spring MVC)
src/main/resources/static/css/style.css
src/main/resources/static/js/app.js
src/main/resources/static/images/logo.png

# AFTER (Quarkus)
src/main/resources/META-INF/resources/css/style.css
src/main/resources/META-INF/resources/js/app.js
src/main/resources/META-INF/resources/images/logo.png
```

## CSRF Token Removal

Quarkus does not use Spring Security's CSRF mechanism. Remove these from templates and JavaScript:

```html
<!-- DELETE -->
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<meta name="_csrf" content="${_csrf.token}">
<meta name="_csrf_header" content="${_csrf.headerName}">
```

```javascript
// DELETE
const token = document.querySelector('meta[name="_csrf"]').content;
const header = document.querySelector('meta[name="_csrf_header"]').content;
headers[header] = token;
```

If the application requires CSRF protection in Quarkus, use `quarkus-csrf-reactive`.

## Common Pitfalls

1. Missing template variables cause runtime failures in Qute strict mode.
2. FreeMarker default operators (`!`) often hide missing data problems that must be fixed explicitly.
3. FreeMarker existence checks (`??`) frequently indicate missing data paths that should be addressed in Java.
4. Convert macros to Qute user tags rather than attempting direct translation.
5. Move formatting and string manipulation into Java instead of reproducing FreeMarker built-ins.
6. Rename `.ftl` files to `.html`.
7. Ensure template locations match `@CheckedTemplate` conventions.
8. Remove Spring Security CSRF integration from templates and JavaScript.
