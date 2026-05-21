# Building TradeChest

## Requirements
- Java 21 or newer (JDK, not JRE)
- Maven 3.8+ **or** a Paper server jar for manual compilation
- A Paper 26.1.2 server jar (for runtime)

## Quick build with Maven

1. Download the Paper API jar and install it locally:

```bash
# Download the Paper server jar from https://papermc.io/downloads/paper
# Then install it into your local Maven repo:
mvn install:install-file \
  -Dfile=paper-26.1.2-XX.jar \
  -DgroupId=io.papermc.paper \
  -DartifactId=paper-api \
  -Dversion=26.1.2-R0.1-SNAPSHOT \
  -Dpackaging=jar
```

2. Update `pom.xml` to use version `26.1.2-R0.1-SNAPSHOT` if Paper's Maven repo is available to you:

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>26.1.2-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

Or use the PaperMC Maven repository directly:
```xml
<repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
</repository>
```

3. Build:
```bash
mvn clean package
```

4. The plugin jar will be at `target/TradeChest-2.0.0.jar`.

## Using IntelliJ IDEA

1. Open the project folder in IntelliJ
2. Add paper-api as a dependency (File → Project Structure → Libraries)
3. Build → Build Artifacts, or run `mvn package` in the terminal panel

## Installing

Drop `TradeChest-2.0.0.jar` into your server's `plugins/` folder and restart.

## What's new in 2.0.0 vs 1.6.2.2

| Issue | Fix |
|---|---|
| `AsyncPlayerChatEvent` removed in Paper 1.20.6+ | Replaced with `AsyncChatEvent` from Paper API |
| Signs blank on modern servers | Rewrote to use Adventure Component API — no §-codes |
| Shop limit setting did nothing | `createShop()` now checks `hasReachedShopLimit()` first |
| Per-player custom limits reset on restart | `loadCustomLimits()` is now called at startup |
| Item comparison ignored enchants/meta | All comparisons use `ItemStack.isSimilar()` |
| No `/reload` command | Added — wired to `Main.reload()` |
| No max purchase quantity | New `max-purchase-quantity` config option |
| Saves to disk on every purchase (main thread) | Async incremental saves; bulk save on shutdown |
| `plugin.yml` version was `1.0-SNAPSHOT` | Fixed to `2.0.0` |
| `api-version: 1.16` in plugin.yml | Updated to `1.21` |
