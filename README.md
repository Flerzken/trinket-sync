✨ Highlights

Load Trinkets on join, save on quit, plus periodic autosave (default: every 5 minutes).

Stores a compressed NBT snapshot (Base64) per player UUID in MySQL.

Safe table auto-creation (optional).

Minimal footprint; no Cobblemon or inventory changes beyond Trinkets.

✅ Requirements

Minecraft 1.21.1

Fabric Loader 0.16.x

Fabric API 0.107.0+1.21.1

Trinkets 3.10.0

Java 21

MySQL 8.x (or compatible)

📦 Installation

Drop the release JAR in the mods/ folder of every Fabric server.

Start the server once to generate the config: config/trinkets-sync.json.

Fill in your MySQL credentials and restart.

Example config (config/trinkets-sync.json):

{
  "mysqlHost": "127.0.0.1",
  "mysqlPort": 3306,
  "mysqlDatabase": "trinkets_sync",
  "mysqlUser": "trinkets",
  "mysqlPassword": "change_me",
  "createTableIfMissing": true,
  "loadOnJoin": true,
  "saveOnQuit": true,
  "autosaveSeconds": 300
}


SQL (only if you don’t let the mod create it automatically):

CREATE DATABASE IF NOT EXISTS trinkets_sync
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE trinkets_sync;
CREATE TABLE IF NOT EXISTS player_trinkets (
  uuid CHAR(36) NOT NULL PRIMARY KEY,
  nbt_base64 MEDIUMTEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP
);

🔧 How it works

On join: reads the player’s Trinkets NBT from MySQL and applies it.

On quit / autosave: writes the current Trinkets NBT back to MySQL (compressed, Base64).

Uses the official Trinkets API (readFromNbt / writeToNbt) with registry lookup for MC 1.21.1.

🧪 Recommended test flow

Start Server A and B pointing to the same MySQL.

Join A, equip a Trinket.

Transfer to B → the item should be present in the same Trinket slot.

Check MySQL table player_trinkets for your UUID entry.

📝 Changelog (v0.2.0)

Initial public release for Fabric 1.21.1.

MySQL storage with HikariCP connection pooling.

Proper NBT compression with NbtSizeTracker.

Compatible with Trinkets 3.10.0 (MC 1.21–1.21.1).

Robust Gradle/GitHub Actions setup (Java 21, reproducible builds).

⚠️ Notes / Known limitations

This mod syncs Trinkets slots only. It does not sync other inventories or Cobblemon-specific data.

Ensure every server in the network points to the same MySQL to avoid divergence.

🗺️ Roadmap (next)

Timestamp/version guard to ignore stale saves.

Admin commands: /trinketsync save|load <player>, /trinketsync debug.

Optional Redis pub/sub for near-instant cross-server sync.

Release automation (tag → GitHub Release with artifacts).
