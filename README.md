# Liminalis

A Paper 1.21.11 plugin. Three lives, one hand-made ability, and the fog between.

Full design and phase plan: `docs/design.md`.

## Layout

```
liminalis-core/     pure Java 21 - NO org.bukkit import, ever
liminalis-plugin/   the Paper adapter: listeners, commands, worldgen, entities, I/O
server/             local test server (gitignored)
```

`core` holds every rule worth being certain about, and has no server dependency, so it is
tested with plain JUnit in milliseconds. The absence of a `paper-api` dependency in
`liminalis-core/pom.xml` is what enforces the split — **do not add one.** If a rule needs
Bukkit types to be expressed, that usually means the rule and the Bukkit plumbing haven't
been separated yet.

## Build

```powershell
mvn clean package          # tests + shaded jar at liminalis-plugin/target/Liminalis-*.jar
.\dev.ps1                  # the above, then deploy into server/plugins
.\dev.ps1 -Run             # ...and start the test server
```

Requires JDK 21 and Maven. Gson is shaded and relocated into the jar, so the plugin does not
depend on the server exposing its own copy.

## Conventions

These are not style preferences — each one exists because of a specific failure it prevents.

1. **`liminalis-core` never imports `org.bukkit`.** The module boundary makes the compiler
   enforce it.
2. **Core logic is written test-first.** If you didn't watch it fail, you don't know it works.
3. **No modifier registers its own listener or schedules its own task.** `ModifierService`
   owns the one listener and the one tick loop and dispatches to whatever is attached.
4. **Every number lives in `config.yml`**, is validated on load, and hot-reloads. An invalid
   config is refused rather than partially applied; at startup it stops the plugin enabling.
5. **Every player-facing string lives in `messages.yml`.** Operator and diagnostic output
   from `/liminalis` is exempt — that is tooling, not the server's voice.
6. **Profiles are versioned, written atomically, and backed up on start.** A missing id or
   life count is a hard error, never a default: guessing either one rewrites who somebody is.
7. **Every subsystem ships with its admin subtree in the same phase** — offline-capable,
   audited, tab-completed from its own registry.

## Admin commands

`/liminalis` (alias `/lim`), permission `liminalis.admin`, with `liminalis.admin.<subsystem>`
underneath so a moderator can be given part of it.

Everything targets **offline players** by resolving names against stored profiles rather than
asking Mojang, so abilities can be assigned while the recipient is asleep. Every mutation is
written to `plugins/Liminalis/audit.log` with before and after values. Destructive commands
must be repeated within 10 seconds.

The subtrees are also the main way features get tested: `injury give` and `singularity spawn`
mean a feature can be exercised on demand rather than waiting on a 30-minute spawn roll.

| Command | |
|---|---|
| `reload [config\|messages\|all]` | re-read configuration |
| `profile <player>` | full state for anyone, online or not |
| `debug <on\|off>` | verbose logging, live |
| `data save <player\|all>` | force a write |
| `data inspect <player>` | raw profile JSON |
| `data backup` | back up now |
| `data reloadfile <player>` | re-read from disk — **destructive**, needs confirmation |

## Status

**Phase 0 (foundation) and Phase 1 (world rules) complete.**

Phase 1 halves player-versus-player damage, halves healing from food, and buffs the
Regeneration effect. Damage is traced back through projectiles, tamed pets and primed TNT to
the player who actually caused it — halving only melee would not reduce fighting, it would
just move it to bows and wolves. Each indirect source can be excluded in config.

*Known gap:* end crystals and player-ignited creepers are not attributed, because neither can
be traced to a player without tracking who placed them.

Later phases — lives and Limbo, traits, blessings and curses, injuries, the Singularity,
revival, abilities, the boss — are described in the design doc and built one at a time, each
verified on a real server before the next.
