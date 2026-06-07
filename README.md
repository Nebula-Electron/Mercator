# Mercator

A NeoForge mod locator that loads mods from a local Maven repository via a mod list file, without requiring them to be placed in the `mods/` folder.

## How it works

Mercator registers as an `IModFileCandidateLocator` and reads a plain-text list of Maven coordinates at launch. Each coordinate is resolved to a JAR in the local repository and injected directly into NeoForge's discovery pipeline.

It also handles mods that ship as an outer wrapper JAR + an inner JarJar JAR sharing the same `modId` (e.g. Sodium), building an overlay so they load correctly without duplicate conflicts.

## Setup

### 1. Build

```bash
mvn package
```

Place the resulting JAR in your NeoForge instance's `libraries/` or equivalent early-classpath location.

### 2. JVM arguments

| Argument | Description | Default |
|---|---|---|
| `-Dmercator.repo.root=<path>` | Path to the root of your local Maven repository | `../../common/modstore` |
| `-Dmercator.mod.list=<path>` | Path to the mod list file | *(required)* |
| `-Dmercator.minecraft.version=<version>` | Target Minecraft version for variant selection | `1.20.1` |


### 3. Mod list file

A plain-text file with one Maven coordinate per line. Blank lines and lines starting with `#` are ignored.

```
# Example mod list
com.example:mymod:1.0.0
net.fabricmc.fabric-api:fabric-api:0.92.0+1.21
```

Mercator will look for a `.patched.jar` variant first when the version contains `+`, falling back to the standard JAR if none is found.
