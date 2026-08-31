# Module: Frontend / View Layer - JSF to Qute

Migrate UI code from Jakarta Faces (JSF) to Quarkus Qute.

## What to do

* [ ] Ensure `quarkus-rest-qute` dependency is present
* [ ] Remove JSF dependencies (`jakarta.faces`, PrimeFaces, OmniFaces, RichFaces, etc.)
* [ ] Replace XHTML JSF pages with Qute templates
* [ ] Remove Facelets templating (`ui:*`)
* [ ] Replace EL expressions with Qute expressions
* [ ] Replace managed beans used only for rendering with DTOs/view models
* [ ] Replace JSF navigation outcomes with REST endpoints
* [ ] Replace JSF forms and validation with standard HTML forms and Bean Validation
* [ ] Replace JSF converters with Java code before rendering
* [ ] Replace JSF AJAX (`f:ajax`, PrimeFaces AJAX) with fetch/XHR if needed
* [ ] Move static resources to `META-INF/resources`
* [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

---

# Dependency

Use `quarkus-rest-qute`:

**Maven**

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-qute</artifactId>
</dependency>
```

**Gradle**

```groovy
implementation 'io.quarkus:quarkus-rest-qute'
```

Remove:

```xml
jakarta.faces
org.primefaces
org.omnifaces
org.richfaces
```

and any JSF-specific servlet configuration.

---

# Core Architecture Changes

## JSF Component Tree → Plain HTML

JSF:

```xhtml
<h:dataTable value="#{userBean.users}" var="user">
    <h:column>
        #{user.name}
    </h:column>
</h:dataTable>
```

Qute:

```html
<table>
    {#for user in users}
    <tr>
        <td>{user.name}</td>
    </tr>
    {/for}
</table>
```

JSF components disappear completely.

---

## Managed Beans → Resource Methods

JSF:

```java
@Named
@RequestScoped
public class UserBean {

    public List<User> getUsers() {
        return service.findAll();
    }
}
```

Qute:

```java
@Path("/users")
public class UserResource {

    @Inject
    UserService service;

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance list(List<User> users);
    }

    @GET
    public TemplateInstance list() {
        return Templates.list(service.findAll());
    }
}
```

Rendering data should come from REST resources rather than JSF backing beans.

---

# Expression Language Conversion

## Value Expressions

| JSF EL          | Qute           |
| --------------- | -------------- |
| `#{user.name}`  | `{user.name}`  |
| `#{bean.value}` | `{bean.value}` |
| `${bean.value}` | `{bean.value}` |

---

## Method Calls

JSF:

```xhtml
#{userBean.displayName(user)}
```

Qute:

```html
{user.displayName}
```

or precompute in Java:

```java
record UserView(String displayName) {}
```

Business logic should generally move out of templates.

---

# Conditional Rendering

JSF:

```xhtml
<h:panelGroup rendered="#{bean.admin}">
    ...
</h:panelGroup>
```

Qute:

```html
{#if admin}
    ...
{/if}
```

---

## Negated Conditions

JSF:

```xhtml
rendered="#{!bean.admin}"
```

Qute:

```html
{#if !admin}
    ...
{/if}
```

---

# Iteration

JSF:

```xhtml
<ui:repeat value="#{users}" var="user">
    ...
</ui:repeat>
```

Qute:

```html
{#for user in users}
    ...
{/for}
```

---

## Data Tables

JSF:

```xhtml
<h:dataTable value="#{users}" var="user">
```

Qute:

```html
<table>
{#for user in users}
<tr>
    <td>{user.name}</td>
</tr>
{/for}
</table>
```

No component equivalent exists.

---

# Facelets Templating

## ui:composition

JSF:

```xhtml
<ui:composition template="/layout.xhtml">
```

Qute:

```html
{#include layout}
    {#title}Users{/title}
{/include}
```

---

## ui:insert

JSF:

```xhtml
<ui:insert name="content"/>
```

Qute:

```html
{#insert content/}
```

---

## ui:define

JSF:

```xhtml
<ui:define name="content">
```

Qute:

```html
{#content}
...
{/content}
```

---

## ui:include

JSF:

```xhtml
<ui:include src="/header.xhtml"/>
```

Qute:

```html
{#include header /}
```

---

# Forms

## Input Text

JSF:

```xhtml
<h:inputText value="#{user.name}" />
```

Qute:

```html
<input name="name" value="{user.name}">
```

---

## Text Area

JSF:

```xhtml
<h:inputTextarea value="#{user.bio}" />
```

Qute:

```html
<textarea name="bio">{user.bio}</textarea>
```

---

## Select

JSF:

```xhtml
<h:selectOneMenu value="#{user.role}">
    <f:selectItems value="#{roles}" />
</h:selectOneMenu>
```

Qute:

```html
<select name="role">
{#for role in roles}
    <option value="{role}">
        {role}
    </option>
{/for}
</select>
```

---

## Submit Buttons

JSF:

```xhtml
<h:commandButton value="Save" action="#{userBean.save}" />
```

Qute:

```html
<form method="post" action="/users/save">
    <button type="submit">Save</button>
</form>
```

The action becomes a normal REST endpoint.

---

# Navigation

JSF:

```java
return "users";
```

JSF navigation outcomes have no equivalent.

Use explicit URLs:

```html
<a href="/users">Users</a>
```

and REST resources:

```java
@Path("/users")
```

---

# Validation

## JSF Validator

JSF:

```xhtml
<f:validateLength minimum="3"/>
```

Move validation into Bean Validation:

```java
public record UserForm(
    @Size(min = 3)
    String name
) {}
```

---

## Validation Messages

JSF:

```xhtml
<h:message for="name"/>
```

Qute:

```html
{#if errors.name}
<div>{errors.name}</div>
{/if}
```

Error maps must be populated manually.

---

# Converters

## f:convertDateTime

JSF:

```xhtml
<f:convertDateTime pattern="yyyy-MM-dd"/>
```

Move formatting into Java:

```java
dto.formattedDate()
```

or

```java
date.format(formatter)
```

before rendering.

---

## Custom Converters

JSF:

```java
@FacesConverter
```

Remove converter classes and transform values before creating the template model.

---

# AJAX

## f:ajax

JSF:

```xhtml
<f:ajax render="table"/>
```

No equivalent.

Replace with:

```javascript
fetch(...)
```

or HTMX if the application uses it.

---

## PrimeFaces AJAX

JSF:

```xhtml
<p:commandButton update="form"/>
```

Replace with:

```javascript
fetch(...)
```

or a page refresh.

---

# PrimeFaces Components

There is no Qute equivalent for:

```xhtml
<p:dataTable>
<p:dialog>
<p:tree>
<p:calendar>
<p:fileUpload>
<p:wizard>
```

Replace them with:

* Native HTML
* JavaScript libraries
* Web Components
* HTMX/Alpine.js (optional)

Treat each PrimeFaces component as a redesign rather than a syntax migration.

---

# Session/View Scope

## ViewScoped

JSF:

```java
@ViewScoped
```

No equivalent exists.

Use:

```java
@RequestScoped
```

or store state in:

```java
@SessionScoped
```

when truly necessary.

Avoid recreating JSF view state behavior.

---

## SessionScoped

JSF:

```java
@SessionScoped
```

Can usually remain:

```java
@SessionScoped
```

if CDI is being used.

However, rendering data should still be supplied by resources.

---

# Template Location

When using `@CheckedTemplate`:

```text
# BEFORE
webapp/users.xhtml

# AFTER
templates/UserResource/users.html
```

Example:

```java
@CheckedTemplate
static class Templates {
    static native TemplateInstance users(List<User> users);
}
```

maps to:

```text
templates/UserResource/users.html
```

---

# Qute Strict Rendering (Critical)

JSF EL usually resolves missing values as `null`.

Qute fails fast.

Example:

```html
{user.name}
{roles}
{errors}
```

Every rendering path must provide all referenced values.

Recommended migration settings:

```properties
quarkus.qute.strict-rendering=false
quarkus.qute.property-not-found-strategy=output-original
```

After migration:

```properties
quarkus.qute.strict-rendering=true
```

Fix all missing variables before enabling strict mode.

---

# Static Resources

```text
# BEFORE
src/main/webapp/resources/css/app.css
src/main/webapp/resources/js/app.js

# AFTER
src/main/resources/META-INF/resources/css/app.css
src/main/resources/META-INF/resources/js/app.js
```

---

# JSF Artifacts to Remove

Delete:

```text
faces-config.xml
web.xml JSF servlet mappings
*.xhtml view files
@ManagedBean
@FacesConverter
@FacesValidator
@FacesComponent
@ViewScoped (JSF variant)
```

Also remove:

```text
javax.faces.*
jakarta.faces.*
PrimeFaces
OmniFaces
RichFaces
```

unless retained for a staged migration.

---

# Migration Rule of Thumb

If a JSF feature relies on:

* component trees
* view state
* server-side UI events
* AJAX partial rendering
* converters
* validators
* navigation outcomes

do **not** search for a Qute equivalent.

Move the behavior into:

* REST endpoints
* CDI services
* Bean Validation
* DTO/view models
* JavaScript (when interactivity is required)

Qute is a template engine, not a UI framework. The most successful migrations simplify the UI architecture instead of attempting a 1:1 JSF feature mapping.
