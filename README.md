# Mod Updater

A lightweight, highly optimized Fabric client mod that automatically checks, downloads, and applies mod updates directly from Modrinth without ever leaving the game.

## ✨ Features

- **Lightning Fast Checks:** Utilizes Modrinth's bulk SHA-1 hash API endpoint to verify dozens of mods in less than a second.
- **100% Accuracy:** Bypasses version naming inconsistencies (like `+fabric` tags) by relying strictly on cryptographic file hashes and Modrinth release dates.
- **Native Mod Menu Integration:** Seamlessly injects its UI into Mod Menu via YACL.
- **Parallel Downloading:** Safely handles batch updates with a multi-threaded asynchronous download manager and live progress tracking.
- **The "Bootstrapper" Architecture:** Safely sidesteps Windows OS file-locking restrictions by extracting a standalone Java utility to swap `.jar` files after the game gracefully closes.

## 📦 Requirements

- **Minecraft:** 26.1.2+
- **Fabric Loader:** >= 0.19.2
- **Java:** 25

### Dependencies

Ensure these mods are in your `mods/` folder alongside this one:

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu)
- [Yet Another Config Lib (YACL)](https://modrinth.com/mod/yacl)

## 🛠️ Building from Source

This project uses the standard Gradle wrapper. To compile the mod yourself:

1. Clone the repository
2. Open a terminal in the project directory.
3. Run the build command:
   - Windows: `.\gradlew.bat build`
   - Linux/Mac: `./gradlew build`
4. Grab the compiled `.jar` file from `build/libs/`.

_Note: The Gradle script will automatically compile and embed the `updater.jar` bootstrapper into the main mod resources._

## ⚠️ Important Note

**Client-Side Only!** Do not install this mod on a dedicated server. It utilizes client-side GUI elements and modifies local machine files. It will cause a server crash on startup.

## 📝 License

This project is licensed under the ARR License.


## ❗ Important:

Do not copy / modify or redistribute this code without permission. It's strictly prohibited.
_"A hard work should never be disrespected."_
