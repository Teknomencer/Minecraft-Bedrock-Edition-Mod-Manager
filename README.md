A simple Android application that automatically finds mod, add-on, and world files on your device and installs them to Minecraft Bedrock Edition (BE).

### Features:
* **Auto Scan:** Detects `.mcworld`, `.mcaddon`, `.mctemplate`, and `.mcpack` files in the device storage.
* **Tabbed Structure:** Lists files in 4 different tabs according to their types (Add-ons, Packs, Templates, Worlds).
* **One-Click Install:** Sends the selected file directly to the `com.mojang.minecraftpe` target via the `ACTION_VIEW` intent and starts the import process.
* **Interface:** Dark/Light mode support.

### Technical Requirements:
* Uses the `MANAGE_EXTERNAL_STORAGE` permission on Android 11 and above for comprehensive file scanning.
