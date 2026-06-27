# Web Config Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build embedded web editor, arena browser, and detailed docs tab for OmniArena, synced to plugin config and arena data.

**Architecture:** Plugin owns data and serves a small HTTP dashboard. The dashboard reads one schema for globals and arenas, edits drafts in browser, and applies changes only when the user clicks Apply. Docs live in the same UI as a separate tab with long-form guides and examples.

**Tech Stack:** Java 21, `com.sun.net.httpserver.HttpServer`, JSON via Gson or Jackson, plain HTML/CSS/JS, Paper API.

---

### Task 1: Web server base

**Files:**
- Create: `src/main/java/com/omniarena/web/WebDashboardServer.java`
- Create: `src/main/java/com/omniarena/web/WebSessionManager.java`
- Modify: `src/main/java/com/omniarena/OmniArena.java`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: Write failing test**

```java
// test that web server refuses when disabled and creates a session URL when enabled
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WebDashboardServerTest`
Expected: fail because classes do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
// start HttpServer, serve /, /api/state, /api/apply, /api/docs
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WebDashboardServerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/omniarena/web src/main/java/com/omniarena/OmniArena.java src/main/resources/config.yml
git commit -m "feat: add web dashboard server"
```

### Task 2: Schema export and apply

**Files:**
- Create: `src/main/java/com/omniarena/web/model/SettingDefinition.java`
- Create: `src/main/java/com/omniarena/web/model/ArenaView.java`
- Create: `src/main/java/com/omniarena/web/model/ConfigSnapshot.java`
- Create: `src/main/java/com/omniarena/web/ConfigSchemaService.java`
- Create: `src/main/java/com/omniarena/web/ConfigApplyService.java`
- Modify: `src/main/java/com/omniarena/config/ConfigManager.java`
- Modify: `src/main/java/com/omniarena/arena/ArenaManager.java`

- [ ] **Step 1: Write failing test**

```java
// test export includes arenas, settings, and docs metadata
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ConfigSchemaServiceTest`
Expected: fail because schema service is missing.

- [ ] **Step 3: Write minimal implementation**

```java
// emit JSON snapshot and apply only changed fields on save
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ConfigSchemaServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/omniarena/web src/main/java/com/omniarena/config src/main/java/com/omniarena/arena
git commit -m "feat: add synced config schema"
```

### Task 3: Web UI and docs tab

**Files:**
- Create: `src/main/resources/web/index.html`
- Create: `src/main/resources/web/app.js`
- Create: `src/main/resources/web/styles.css`
- Create: `src/main/resources/web/docs.md`
- Modify: `src/main/java/com/omniarena/web/WebDashboardServer.java`

- [ ] **Step 1: Write failing test**

```java
// test dashboard serves page shell, arena list, and docs text
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WebDashboardServerUiTest`
Expected: fail because resources are missing.

- [ ] **Step 3: Write minimal implementation**

```html
<!-- tabs: dashboard, arenas, config, docs -->
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WebDashboardServerUiTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/web src/main/java/com/omniarena/web
git commit -m "feat: add web dashboard ui"
```

### Task 4: Docs content

**Files:**
- Create: `src/main/resources/web/docs.md`
- Create: `src/main/java/com/omniarena/web/DocsCatalog.java`

- [ ] **Step 1: Write failing test**

```java
// test docs contain setup, commands, arenas, kits, regen, integrations, troubleshooting
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DocsCatalogTest`
Expected: fail because docs catalog is missing.

- [ ] **Step 3: Write minimal implementation**

```java
// build long detailed docs sections from markdown
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DocsCatalogTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/web/docs.md src/main/java/com/omniarena/web/DocsCatalog.java
git commit -m "feat: add detailed docs tab"
```

