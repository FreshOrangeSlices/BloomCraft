# BloomCraft: notes for working on the mod

Fabric mod for Minecraft 26.3 (Mojang names, no remapping), Java 25. The mod version of the Bosses3 server plugins
(github.com/FreshOrangeSlices/Bosses3), built in stages: Bosses (done), Pets, Quest Book + HUDs, Skills.

## Building and checking

- CI (`.github/workflows/build.yml`) compiles and runs the server game tests (`src/gametest`) on every push; the
  jar is the **BloomCraft** artifact. Failed builds report their errors as check-run annotations
  (`gh api repos/FreshOrangeSlices/BloomCraft/check-runs/<job id>/annotations`), because job logs aren't readable here.
- Local type-check in seconds: `tools/dev/fetch-kit.sh` once (downloads `.devkit/` from the `dev-kit` branch: a JDK 25
  plus signature-only stubs of Minecraft, Fabric API and libraries), then `tools/dev/check.sh` (javac on main, client and
  gametest, plus `mixincheck.py`, which verifies every injector's target method is declared in its target class).
  Rebuild the kit with the "Dev kit (development)" workflow after changing Minecraft / Fabric API versions.
- API lookups: `tools/dev/api.sh find <regex>` and `tools/dev/api.sh <class>` (javap on the stubs). For bytecode of a
  method (to aim a mixin) or vanilla resource files (models, recipes, tags), dispatch "API lookup (development)"
  with `code` / `files` inputs; the result lands in `api.txt` on the `dev-api` branch.
- In-game screenshots: dispatch "Screenshots (development)": the real client runs `ScreenshotTest` (client game test)
  and pushes the PNGs and log excerpts to the `dev-shots` branch.
- Textures: `python3 tools/textures.py --preview /tmp/preview.png` regenerates every item/block texture, model and item
  definition from code (PIL). Commit the generated files.

## Code layout

- `core/`: shared helpers (Data = per-entity saved data via Fabric attachments, WorldStore = the world's own save file,
  Scheduler, Ticks, Clock, Fx, Text with MiniMessage-style tags, ServerMenu for chest menus run by the server,
  Spawns for effect mobs that are never saved / visible to one player).
- `bosses/`: everything from the Additional Bosses plugin (bosses, traits, combat pipeline, relics and curses, items,
  Nemesis, statues, waystones, guide books, commands). Config: `config/bloomcraft/bosses.yml` (defaults in the jar
  at `bloomcraft/bosses.yml`; missing keys in a player's file fall back to the defaults).
- `mixin/`: every mixin. Keep them small; logic lives in the managers.

## Conventions

- Tabs, Javadoc on classes and non-obvious methods, player-facing wording in plain English.
- Commit straight to main with the Co-Authored-By trailer; CI must stay green (build + game tests).
