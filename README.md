# AntiDumper

**Ultimate Minecraft Server Protection** — One plugin to shield your server from world downloaders, plugin stealers, hacked clients, bots, exploits, and more.

---

## Features

### 🛡️ Protection Modules (30+)

| Category | Modules |
|----------|---------|
| **World Downloader** | AntiWDL (30+ mods detected), Litematica, Baritone, Xaero, JourneyMap, VoxelMap |
| **Plugin Theft** | AntiPluginStealer, AntiFileSteal, AntiCommandDump |
| **Hacked Clients** | LPX, LiquidBounce, Wurst, Future, Impact, Meteor, Aristois, 20+ more |
| **Exploits** | AntiCrash, PacketLimiter, AntiBookBan, AntiChunkBan, AntiLagMachine, AntiPhase, AntiBlink, AntiTimer, AntiNoFall, AntiBoatFly, AntiElytraFly, AntiAutoPearl, AntiInventoryMove, AntiDisabler, AntiTower, AntiScaffold, AntiNBT, AntiCommandExploit |
| **Bot Protection** | CAPTCHA, Behaviour Check, Siege Mode, UUID Whitelist |
| **Network** | Connection Limiter, Packet Inspector, Command Whitelist |

### 🌐 Multi-Platform — Single JAR

| Platform | Mode |
|----------|------|
| **Paper / Purpur / Spigot / Bukkit** | Full protection |
| **Folia** | Full (regional scheduler) |
| **BungeeCord / Waterfall** | Proxy bridge |
| **Velocity** | Proxy bridge |

Drop the same JAR in `plugins/` on any platform — auto-detects the environment.

### 💾 Database

- **H2** (embedded, pure Java)
- **SQLite** (embedded, native)
- **MySQL** (external server)

Configurable in `config.yml` — switch anytime.

### 🔄 Redis Sync

Real-time cross-server synchronization. Share violations, punishments, and siege mode state across your entire server network.

---

## Quick Start

1. Download the latest JAR from [Releases](https://github.com/S1sTeam/antidumper/releases)
2. Place it in your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/AntiDumper/config.yml` to your needs
5. Run `/ad reload`

**No required dependencies.**

---

## Commands

| Command | Description |
|---------|-------------|
| `/ad` or `/antidumper` | Main command |
| `/ad gui` | Open GUI control panel |
| `/ad reload` | Reload configuration |
| `/ad check <player>` | Scan player for suspicious mods |
| `/ad stats` | Violation statistics |
| `/ad punish <player> [reset]` | Punishment info / reset |
| `/ad whitelist add\|remove\|list <cmd>` | Manage command whitelist |
| `/ad module list` | List all modules with status |
| `/ad module toggle <name>` | Enable/disable module live |
| `/ad module disable_all` | Emergency disable all modules |

---

## PlaceholderAPI

```
%antidumper_version%           %antidumper_platform%
%antidumper_database%          %antidumper_redis%
%antidumper_server_name%       %antidumper_online_current%
%antidumper_online_peak%       %antidumper_online_average%
%antidumper_violations_total%  %antidumper_modules_total%
%antidumper_player_violations% %antidumper_player_punish_level%
%antidumper_player_name%       %antidumper_player_uuid%
%antidumper_player_bypass%     %antidumper_lang%
%antidumper_module_<name>%     %antidumper_geo_<player>_country%
```

---

## Requirements

- **Java:** 21+ (Java 25 supported for MC 26.1.2)
- **Server:** Paper 1.21.3+ (also works on Purpur, Spigot, Folia)
- **Proxy (optional):** BungeeCord/Waterfall or Velocity

---

## Building from Source

```bash
git clone https://github.com/S1sTeam/antidumper.git
cd antidumper
mvn clean package
```

JAR will be in `target/AntiDumper-3.0.jar`.

---

## License

All rights reserved © 2026 S1sTeam
