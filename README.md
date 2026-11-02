# 🌍 HoI4 Modder Studio V2

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF.svg?logo=kotlin)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Compose-Desktop-4285F4.svg?logo=android)](https://www.jetbrains.com/lp/compose-multiplatform/)

A modern, fast, and cross-platform modding suite for *Hearts of Iron IV*.
Built with Kotlin Multiplatform and Jetpack Compose, this tool replaces tedious text-file editing and the instable in-game Nudge tool with a clean, IDE-like workspace.

![HoI4 Modder Main Interface](assets/assets/example_screenshot.png)

## Features

* **Interactive Map Workspace:** Seamlessly pan and zoom across the HoI4 map. Paint province definitions, adjust terrain, and toggle coastal flags directly on the canvas.
* **Smart Assignment:** No more typing IDs. Assign provinces to States and Strategic Regions using a visual point-and-click Map Pick Mode.
* **Infrastructure Builder:** Draw railways visually node-by-node. Place supply hubs, naval bases, and industrial complexes exactly where you want them.
* **Conflict Diagnostics:** Built-in scanner to detect common crash causes (e.g., empty states, duplicate assignments, self-looping railways, or undefined RGB values on the map).
* **Auto-Fix Capabilities:** Automatically recalculate province centroids, generate missing building spawns, and fix coastal flags based on land-sea adjacencies.
* **Modern Desktop UI:** Features an Eclipse/IntelliJ-inspired workspace with detachable route tabs, dark/light themes, and a robust undo/redo system.

## Getting Started

This project targets Desktop (JVM) utilizing the Kotlin Multiplatform toolchain.

### Prerequisites
* Java Development Kit (JDK) 17 or higher
* A valid installation of *Hearts of Iron IV* (Vanilla)
* Your custom Mod Directory

### Building and Running

Use the Gradle wrapper to build and launch the Desktop application directly from your terminal:

```bash
# Standard Run
./gradlew :desktopApp:run

# Hot Reload Run (Experimental Compose Feature)
./gradlew :desktopApp:hotRun --auto