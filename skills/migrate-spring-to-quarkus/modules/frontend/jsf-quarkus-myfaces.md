# Module: Frontend / View Layer - JSF with Quarkus (MyFaces)

Migrate Jakarta Faces (JSF) applications to Quarkus while maintaining JSF using Apache MyFaces and PrimeFaces Quarkus extensions.

## What to do

* [ ] Add MyFaces Quarkus extension
* [ ] Add PrimeFaces Quarkus extension (if using PrimeFaces)
* [ ] Update JSF dependencies to Jakarta namespace
* [ ] Create or verify `web.xml` with JSF servlet configuration
* [ ] Migrate faces properties from Spring config to `web.xml`
* [ ] Clean `faces-config.xml` of Spring references
* [ ] Update `faces-config.xml` to Jakarta Faces 4.x
* [ ] Update XHTML namespaces to Jakarta Faces
* [ ] Convert JSF managed beans to CDI beans
* [ ] Replace Spring scope annotations with CDI equivalents
* [ ] Convert JSF managed property injection to CDI injection
* [ ] Review custom EL resolvers and remove Spring-specific JSF integrations
* [ ] Move XHTML files to Quarkus resource locations (must be under `META-INF/resources`)
* [ ] Move static resources to Quarkus resource locations
* [ ] Update converters to use `managed = true` if they need CDI
* [ ] Update validators to use `managed = true` if they need CDI
* [ ] Verify navigation and URL mappings
* [ ] Verify converters and validators
* [ ] Verify AJAX functionality
* [ ] Verify session and view scoped beans
* [ ] Test in Quarkus dev mode: `./mvnw quarkus:dev`
* [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

---

## When to Use This Guide

Use this guide when:

* You want to **keep JSF** and migrate the runtime from Spring to Quarkus
* Your application heavily uses JSF/PrimeFaces components
* You need a lower-risk migration path
* You plan to modernize the UI later

For a complete UI modernization (JSF → Qute), see `jsf-qute.md`.

---

## Version Compatibility

Ensure compatible versions:

* Quarkus 3.x → Jakarta EE 10 → Jakarta Faces 4.x
* MyFaces Quarkus extension version should match your Quarkus platform version
* PrimeFaces 12+ for Jakarta Faces 4.x support
* PrimeFaces 13+ recommended for best Quarkus compatibility

---

# Dependency

## Add MyFaces Quarkus Extension

Use the Quarkus extension plugin instead of manually specifying versions:

**Maven:**

```bash
./mvnw quarkus:add-extension \
  -Dextensions="org.apache.myfaces.core.extensions.quarkus:myfaces-quarkus"
```

**Gradle:**

```bash
./gradlew addExtension --extensions="org.apache.myfaces.core.extensions.quarkus:myfaces-quarkus"
```

---

## Add PrimeFaces Quarkus Extension (Optional)

If the application uses PrimeFaces:

**Maven:**

```bash
./mvnw quarkus:add-extension \
  -Dextensions="io.quarkiverse.primefaces:quarkus-primefaces"
```

**Gradle:**

```bash
./gradlew addExtension --extensions="io.quarkiverse.primefaces:quarkus-primefaces"
```

The Quarkus extension tooling will select a compatible version for the current Quarkus platform.

---

# web.xml Configuration

## Create or Verify web.xml

JSF requires a `web.xml` file with servlet configuration. This file must be located at:

```text
src/main/resources/META-INF/resources/web.xml
```

**If `web.xml` is missing, create it with minimal JSF configuration:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
         version="5.0">
    
    <servlet>
        <servlet-name>Faces Servlet</servlet-name>
        <servlet-class>jakarta.faces.webapp.FacesServlet</servlet-class>
        <load-on-startup>1</load-on-startup>
    </servlet>
    
    <servlet-mapping>
        <servlet-name>Faces Servlet</servlet-name>
        <url-pattern>*.xhtml</url-pattern>
    </servlet-mapping>
    
    <welcome-file-list>
        <welcome-file>index.xhtml</welcome-file>
    </welcome-file-list>
</web-app>
```

---

## Migrate Faces Properties to web.xml

When migrating from Spring, JSF context parameters must be moved from `application.properties` or Spring configuration to `web.xml`.

**Common parameters to migrate:**

```xml
<context-param>
    <param-name>jakarta.faces.PROJECT_STAGE</param-name>
    <param-value>Development</param-value>
</context-param>

<context-param>
    <param-name>jakarta.faces.STATE_SAVING_METHOD</param-name>
    <param-value>server</param-value>
</context-param>

<context-param>
    <param-name>jakarta.faces.FACELETS_SKIP_COMMENTS</param-name>
    <param-value>true</param-value>
</context-param>

<context-param>
    <param-name>jakarta.faces.FACELETS_REFRESH_PERIOD</param-name>
    <param-value>2</param-value>
</context-param>
```

**Note:** Some MyFaces-specific properties can remain in `application.properties` using the `quarkus.myfaces.*` prefix (see Configuration section).

---

# Pre-Migration Audit

Before modifying code, identify JSF-specific features currently in use:

* [ ] JSF managed beans (`@ManagedBean`)
* [ ] Session scoped beans
* [ ] View scoped beans
* [ ] Custom converters
* [ ] Custom validators
* [ ] Custom EL resolvers
* [ ] PrimeFaces components
* [ ] PrimeFaces dialogs
* [ ] PrimeFaces file uploads
* [ ] PrimeFaces data exporters
* [ ] PrimeFaces lazy loading models
* [ ] Custom `faces-config.xml` entries
* [ ] Custom JSF exception handlers
* [ ] URL mappings and navigation rules

---

# Managed Beans Migration

## From JSF Managed Beans to CDI

**Before**

```java
import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;

@ManagedBean
@RequestScoped
public class UserBean {
}
```

**After**

```java
import jakarta.inject.Named;
import jakarta.enterprise.context.RequestScoped;

@Named
@RequestScoped
public class UserBean {
}
```

---

## Scope Mapping

| JSF Legacy           | Jakarta CDI                                    |
| -------------------- | ---------------------------------------------- |
| `@ManagedBean`       | `@Named`                                       |
| `@RequestScoped`     | `jakarta.enterprise.context.RequestScoped`     |
| `@SessionScoped`     | `jakarta.enterprise.context.SessionScoped`     |
| `@ApplicationScoped` | `jakarta.enterprise.context.ApplicationScoped` |
| `@ViewScoped`        | `jakarta.faces.view.ViewScoped`                |

---

## ⚠️ CRITICAL: Spring Scope Annotations Must Be Replaced

If your managed beans use Spring scope annotations, they **MUST** be converted to CDI scopes. Spring annotations will cause runtime errors in Quarkus.

**❌ WRONG - Spring scope annotations (will cause runtime errors):**

```java
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.beans.factory.annotation.Qualifier;

@SessionScope  // Spring annotation - WRONG!
public class MyBean {
    @Inject
    @Qualifier("facesContext")  // Spring qualifier - WRONG!
    private FacesContext context;
}
```

**✓ CORRECT - CDI scope annotations:**

```java
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import jakarta.faces.context.FacesContext;

@Named
@SessionScoped  // CDI annotation - CORRECT!
public class MyBean implements Serializable {
    // FacesContext should NOT be injected - use getCurrentInstance()
    private FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }
    
    public void someMethod() {
        FacesContext context = getFacesContext();
        // Use context...
    }
}
```

**Spring to CDI Scope Conversion:**

| Spring Annotation                                      | CDI Equivalent                                 |
| ------------------------------------------------------ | ---------------------------------------------- |
| `@org.springframework.web.context.annotation.SessionScope` | `@jakarta.enterprise.context.SessionScoped`    |
| `@org.springframework.web.context.annotation.RequestScope` | `@jakarta.enterprise.context.RequestScoped`    |
| `@org.springframework.context.annotation.Scope("prototype")` | `@jakarta.enterprise.context.Dependent`        |

**Validation Steps:**

After migrating managed beans, verify:

1. No Spring scope annotations remain:
   ```bash
   grep -r '@SessionScope\|@RequestScope' src/main/java/
   ```

2. No Spring `@Qualifier` for FacesContext:
   ```bash
   grep -r '@Qualifier.*facesContext' src/main/java/
   ```

3. All FacesContext usage follows `getCurrentInstance()` pattern

---

## ⚠️ CRITICAL: FacesContext Injection Pattern

**FacesContext Cannot Be Injected in Quarkus**

**❌ WRONG - Spring-style injection:**

```java
@Inject
@Qualifier("facesContext")
private FacesContext context;
```

**✓ CORRECT - JSF standard pattern:**

```java
// Use helper method instead of injection
private FacesContext getFacesContext() {
    return FacesContext.getCurrentInstance();
}

// Use in methods
public void addMessage(String message) {
    FacesContext context = getFacesContext();
    context.addMessage(null, new FacesMessage(message));
}
```

**Why FacesContext Cannot Be Injected:**

* FacesContext is request-scoped and managed by the JSF framework
* It's not a CDI bean and cannot be injected
* Spring's `@Qualifier` approach doesn't work in Quarkus
* Always use `FacesContext.getCurrentInstance()` - this is the JSF standard

---

## Managed Property Injection

**Before**

```java
@ManagedProperty("#{userService}")
private UserService userService;
```

**After**

```java
@Inject
UserService userService;
```

---

# CDI Bean Discovery

Verify all JSF backing beans are CDI beans.

Common symptom:

```text
#{userBean}
Property 'userBean' not found
```

Typical causes:

* Missing `@Named`
* Missing CDI scope annotation
* Bean not discovered by CDI

Example:

```java
@Named
@RequestScoped
public class UserBean {
}
```

---

# XHTML Migration

## Namespace Updates

**Before**

```xhtml
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="http://xmlns.jcp.org/jsf/html"
      xmlns:f="http://xmlns.jcp.org/jsf/core"
      xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
```

**After**

```xhtml
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="jakarta.faces.html"
      xmlns:f="jakarta.faces.core"
      xmlns:ui="jakarta.faces.facelets">
```

---

# faces-config.xml

## Update Schema to Jakarta Faces 4.x

```xml
<faces-config
    xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        https://jakarta.ee/xml/ns/jakartaee
        https://jakarta.ee/xml/ns/jakartaee/web-facesconfig_4_0.xsd"
    version="4.0">
</faces-config>
```

---

## Clean faces-config.xml of Spring References

When migrating from Spring, `faces-config.xml` often contains Spring-specific configurations that must be removed.

**Remove Spring EL Resolver:**

```xml
<!-- ❌ DELETE THIS -->
<application>
    <el-resolver>org.springframework.web.jsf.el.SpringBeanFacesELResolver</el-resolver>
</application>
```

**Remove Spring Bean References:**

```xml
<!-- ❌ DELETE THIS -->
<managed-bean>
    <managed-bean-name>userService</managed-bean-name>
    <managed-bean-class>org.springframework.web.jsf.DelegatingVariableResolver</managed-bean-class>
</managed-bean>
```

**Remove Spring Security Configurations:**

```xml
<!-- ❌ DELETE THIS -->
<lifecycle>
    <phase-listener>org.springframework.security.web.jsf.FacesContextPhaseListener</phase-listener>
</lifecycle>
```

**Keep Standard JSF Configurations:**

```xml
<!-- ✓ KEEP THESE -->
<navigation-rule>
    <!-- Your navigation rules -->
</navigation-rule>

<converter>
    <!-- Your custom converters -->
</converter>

<validator>
    <!-- Your custom validators -->
</validator>
```

**Checklist:**

* [ ] Remove Spring EL resolver
* [ ] Remove Spring bean references
* [ ] Remove Spring security phase listeners
* [ ] Keep standard JSF navigation rules
* [ ] Keep standard JSF converters and validators
* [ ] Verify all EL expressions resolve correctly after cleanup

---

# Custom EL Resolvers

Review all remaining EL resolvers configured in `faces-config.xml`.

Any custom EL resolvers that depend on Spring must be refactored to use CDI instead.

Replace Spring bean lookups with CDI-managed beans using `@Named` and `@Inject`.

---

# Converters and Validators

## CDI-Aware Converters

If a converter uses dependency injection:

```java
@Inject
UserService userService;
```

ensure it is CDI managed.

Example:

```java
@FacesConverter(
    value = "userConverter",
    managed = true
)
public class UserConverter implements Converter<User> {

    @Inject
    UserService userService;

}
```

Without CDI management, injected dependencies may be `null`.

---

## CDI-Aware Validators

Example:

```java
@FacesValidator(
    value = "emailValidator",
    managed = true
)
public class EmailValidator implements Validator<String> {
}
```

Verify:

* [ ] Converter injection works
* [ ] Validator injection works
* [ ] Custom converters resolve correctly
* [ ] Custom validators resolve correctly

---

# ViewScoped Beans

View scoped beans must remain serializable.

```java
@Named
@ViewScoped
public class UserBean implements Serializable {
}
```

Verify:

* [ ] Bean implements `Serializable`
* [ ] Large object graphs are not stored unnecessarily
* [ ] Postback behavior works correctly
* [ ] Browser refresh behavior is acceptable

---

# Resource Migration

## ⚠️ CRITICAL: XHTML Files Location

XHTML pages **MUST** be located under:

```text
src/main/resources/META-INF/resources
```

**They will NOT be served from any other location.**

**Correct structure:**

```text
src/main/resources/META-INF/resources/
├── index.xhtml
├── users/
│   ├── list.xhtml
│   └── edit.xhtml
├── css/
│   └── app.css
├── js/
│   └── app.js
└── images/
    └── logo.png
```

**Incorrect locations (will not work):**

```text
❌ src/main/webapp/
❌ src/main/resources/templates/
❌ src/main/resources/static/
```

**Verification checklist:**

* [ ] All XHTML files copied to `META-INF/resources`
* [ ] Directory structure preserved
* [ ] CSS loads correctly
* [ ] JavaScript loads correctly
* [ ] Images load correctly
* [ ] PrimeFaces resources load correctly

---

# Navigation Verification

Verify all JSF navigation flows.

Checklist:

* [ ] Implicit navigation
* [ ] Redirect navigation
* [ ] Bookmarkable URLs
* [ ] Browser refresh behavior
* [ ] Navigation cases in `faces-config.xml`

Example:

```java
return "users/list?faces-redirect=true";
```

---

# PrimeFaces Verification

If PrimeFaces is used, specifically test:

* [ ] DataTable
* [ ] LazyDataModel
* [ ] Dialogs
* [ ] AJAX updates
* [ ] FileUpload
* [ ] DataExporter
* [ ] Charts
* [ ] Dynamic dialogs
* [ ] WebSocket or Push features

These components are typically the most sensitive during migration.

---

# Configuration

Add MyFaces configuration to `application.properties`:

**Development:**

```properties
# MyFaces configuration for development
quarkus.myfaces.project-stage=Development
quarkus.myfaces.facelets-refresh-period=2

# Enable detailed error messages
quarkus.myfaces.error-handler=true
```

**Production:**

```properties
# MyFaces configuration for production
quarkus.myfaces.project-stage=Production
quarkus.myfaces.facelets-refresh-period=-1

# Disable detailed error messages
quarkus.myfaces.error-handler=false
```

**Common Settings:**

```properties
# State saving
quarkus.myfaces.state-saving-method=server

# View state encryption (recommended for production)
quarkus.myfaces.use-encryption=true

# Resource handling
quarkus.myfaces.resource-max-time-expires=604800000
```

---

# Testing

## Verify in Quarkus Dev Mode

Start the application in development mode:

**Maven:**

```bash
./mvnw quarkus:dev
```

**Gradle:**

```bash
./gradlew quarkusDev
```

## Test Checklist

* [ ] All pages load without errors
* [ ] Navigation works correctly
* [ ] Forms submit successfully
* [ ] Validation messages display
* [ ] AJAX functionality works
* [ ] Session state persists correctly
* [ ] View state survives postbacks
* [ ] PrimeFaces components render correctly
* [ ] Static resources load (CSS, JS, images)
* [ ] Converters work as expected
* [ ] Validators work as expected

---

# Troubleshooting

## Bean Not Found Error

```text
Property 'userBean' not found
```

**Causes:**

* Missing `@Named` annotation
* Missing CDI scope annotation
* Bean not discovered by CDI

**Solution:**

```java
@Named
@RequestScoped
public class UserBean {
}
```

---

## Converter/Validator Injection Returns Null

**Problem:** Injected dependencies in converters or validators are `null`.

**Solution:** Add `managed = true` to enable CDI:

```java
@FacesConverter(
    value = "userConverter",
    managed = true
)
public class UserConverter implements Converter<User> {
    @Inject
    UserService userService;
}
```

---

## Resources Not Loading

**Problem:** CSS, JavaScript, or images return 404.

**Solution:** Verify resources are under:

```text
src/main/resources/META-INF/resources/
```

Not:

```text
src/main/webapp/resources/
```

---

## ViewScoped Bean Not Serializable

**Problem:**

```text
java.io.NotSerializableException: com.example.UserBean
```

**Solution:** Implement `Serializable`:

```java
@Named
@ViewScoped
public class UserBean implements Serializable {
    private static final long serialVersionUID = 1L;
}
```

---

## Namespace Not Recognized

**Problem:** XHTML pages show errors with Jakarta namespaces.

**Solution:** Update all namespaces from:

```xhtml
xmlns:h="http://xmlns.jcp.org/jsf/html"
```

to:

```xhtml
xmlns:h="jakarta.faces.html"
```

---

# Common Pitfalls

1. **Missing or incorrect `web.xml`** - JSF requires `web.xml` in `src/main/resources/META-INF/resources/web.xml` with servlet configuration
2. **Not migrating faces properties to `web.xml`** - JSF context parameters must be in `web.xml`, not just `application.properties`
3. **Not cleaning `faces-config.xml` of Spring references** - Spring EL resolvers, bean references, and security configs must be removed
4. **Using Spring scope annotations** (`@SessionScope`, `@RequestScope`) instead of CDI equivalents - causes runtime errors
5. **Attempting to inject FacesContext** with `@Qualifier("facesContext")` - use `FacesContext.getCurrentInstance()` instead
6. **XHTML files not under `META-INF/resources`** - pages will not be served from any other location
7. **Forgetting `managed = true`** on converters/validators that use CDI injection - results in null dependencies
8. **Missing `@Named` annotation** on CDI beans - causes "Property not found" errors in EL expressions
9. **Not implementing `Serializable`** on `@ViewScoped` beans - causes serialization errors
10. **Forgetting to update XHTML namespaces** from `xmlns.jcp.org` to `jakarta.faces` - causes parsing errors
11. **Missing CDI scope annotations** alongside `@Named` - bean won't be discovered
12. **Not updating `faces-config.xml` schema** to Jakarta Faces 4.x - may cause compatibility issues
13. **Mixing javax and jakarta imports** - causes ClassNotFoundException at runtime
14. **Not testing in Quarkus dev mode** before production build - misses runtime issues

---

# Migration Checklist

* [ ] Add MyFaces Quarkus extension
* [ ] Add PrimeFaces extension (if needed)
* [ ] Create or verify `web.xml` in `src/main/resources/META-INF/resources/web.xml`
* [ ] Migrate faces properties from Spring config to `web.xml`
* [ ] Clean `faces-config.xml` of Spring EL resolvers and bean references
* [ ] Update `faces-config.xml` schema to Jakarta Faces 4.x
* [ ] Update XHTML namespaces from `xmlns.jcp.org` to `jakarta.faces`
* [ ] Convert `@ManagedBean` to `@Named`
* [ ] Replace Spring scope annotations (`@SessionScope`) with CDI equivalents (`@SessionScoped`)
* [ ] Convert `@ManagedProperty` to `@Inject`
* [ ] Replace FacesContext injection with `getCurrentInstance()` pattern
* [ ] Review and remove Spring-specific EL resolvers
* [ ] Move XHTML files to `META-INF/resources` (CRITICAL - must be in this location)
* [ ] Move static resources to `META-INF/resources`
* [ ] Add `managed = true` to converters that use CDI injection
* [ ] Add `managed = true` to validators that use CDI injection
* [ ] Verify ViewScoped beans implement `Serializable`
* [ ] Verify session management
* [ ] Verify navigation
* [ ] Verify AJAX functionality
* [ ] Verify PrimeFaces components (if used)
* [ ] Test in Quarkus dev mode: `./mvnw quarkus:dev`
* [ ] Build and validate production package: `./mvnw clean package`
