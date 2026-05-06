# VortexBlueprints

VortexBlueprints is a blueprint marketplace plugin for Paper and Folia servers. Players can capture structures, publish them to a curated market, sell build licenses, earn royalties when others place their work, and moderate originality disputes through both in-game GUIs and a built-in web panel.

## Highlights

- Cuboid blueprint capture with a wand or `/vbp pos1` and `/vbp pos2`
- SQLite or MySQL-backed marketplace storage
- Originality classification with duplicate, suspicious, and derived-build handling
- License sales, repeat-use tracking, milestone progression, and owner-share royalty splits
- Safe placement flow with material readiness, collision, liquid, and protection checks
- Market browsing by curation view and category filter
- Creator analytics, staff curation, disputes, and review queue moderation
- Built-in web panel plus Discord webhook notifications
- Bukkit API events for publication, purchase, build completion, and royalty payouts

## Requirements

- Java 21
- Paper 1.21.x or Folia 1.21.x
- Optional: Vault and an economy plugin
- Optional: WorldGuard, Towny, Lands, GriefPrevention, Residence, PlotSquared

## Quick Start

1. Drop `VortexBlueprints-1.0.0.jar` into `plugins/`.
2. Start the server once.
3. Edit `plugins/VortexBlueprints/config.yml`.
4. Restart or run `/vbp reload`.
5. Use `/vbp wand`, mark a selection, and publish a listing with `/vbp save`.

## Core Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/vbp wand` | `vortexblueprints.create` | Get the selection wand |
| `/vbp pos1` / `/vbp pos2` | `vortexblueprints.create` | Set corners at your current block |
| `/vbp save <id> <price> [royalty] [category] [license] [style] [description...]` | `vortexblueprints.create` | Publish the selected build |
| `/vbp market [view] [category] [page]` | `vortexblueprints.use` | Open the marketplace |
| `/vbp build <id>` | `vortexblueprints.build` | Build a blueprint at your location |
| `/vbp preview <id>` | `vortexblueprints.build` | Show the blueprint footprint |
| `/vbp info <id>` | `vortexblueprints.use` | Inspect a listing |
| `/vbp analytics` | `vortexblueprints.use` | Open creator analytics |
| `/vbp rate <id>` | `vortexblueprints.use` | Open the GUI rating flow for a purchased listing |
| `/vbp review gui` | `vortexblueprints.admin` | Open the review queue GUI |
| `/vbp dispute gui` | `vortexblueprints.admin` | Open the dispute queue GUI |
| `/vbp team gui <id>` | Owner or admin | Open the team ownership editor |
| `/vbp panel` | `vortexblueprints.admin` | Print the built-in web panel URL |
| `/vbp probe protection [x] [y] [z]` | `vortexblueprints.admin` | Diagnose claim-hook results at a location |

## Build

```powershell
./gradlew.bat build
```

The packaged jar is copied to `jar/VortexBlueprints-1.0.0.jar`.

## Wiki

Full setup, configuration, API, and moderation documentation is published in the GitHub wiki:

https://github.com/Sauron05/VortexBluePrints/wiki