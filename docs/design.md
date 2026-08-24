# Liminalis — Paper 1.21.11 Plugin

> **Status:** Phases 0-4 complete, verified on a real 1.21.11 server. Phase 5 (injuries and
> mortal wounds) is next.
>
> **Limbo is a bare, treeless pale garden.** The biome carries its own grey palette, dark fog,
> water tint and ambient sound for free. Caves, structures, mobs and decorations are all
> generated off; surface generation stays on, so the pale moss ground remains with nothing
> growing on it. Difficulty is Peaceful and exhaustion is cancelled, so saturation never drops.
>
> **Traits, blessings and curses are Java classes whose every number is read from config at
> the point of use**, so `/liminalis reload` genuinely rebalances a live server rather than
> reporting that it did. Two primitives carry most of the roster: `DynamicAttributeSource`
> (Resilience and Coward are the same curve with different parameters) and `Restriction`
> (the "cannot wear Protection IV" class of curse cost).
>
> Known gaps, all deliberate:
> - End crystals and player-ignited creepers are not attributed as player damage.
> - The third death drops your inventory in the overworld vanilla-style, so anyone revived
>   later returns empty-handed.
> - Turning Limbo decorations back on would only affect newly generated chunks.
> - Curse costs are currently armour restrictions and attribute penalties. A held-item
>   restriction (a curse that forbids swords) would need a new hook.

## Context

A survival server built around scarcity and mystery: every player gets **three lives**, a
**unique hand-coded ability**, and a randomised identity (traits, and possibly a blessing or
curse) rolled on first join. Large damage inflicts **injuries** matched to the damage type;
massive damage inflicts **mortal wounds**. Spending all three lives sends you to **Limbo**, a
shared fog dimension you cannot die in and cannot leave without a living player mounting an
expedition to retrieve you. The **Singularity** periodically drops creatures into the world
whose lore books are the only source of knowledge about any of it — including how revival works.
PvP damage is halved, food healing is halved and regeneration is buffed, all to push players
toward cooperation ahead of an eventual boss that demands it.

Nothing exists yet. This is a greenfield build, and the explicit goal is quality over speed:
one vertical slice at a time, each verified on a real server before the next begins.

---

## Locked decisions

| | |
|---|---|
| **Platform** | Paper `1.21.11-R0.1-SNAPSHOT` (confirmed live on repo.papermc.io) |
| **Toolchain** | Maven 3.9.14, Temurin JDK 21.0.10 (both verified installed) |
| **Name** | Liminalis — `/liminalis` root command, `liminalis.*` permissions |
| **Lives** | 3 lives; the **3rd death** sends you to Limbo |
| **Deaths that count** | All in-world deaths; admin/technical excused manually. **PvP counting is toggleable by command** |
| **Revival result** | Return with **2 lives** + a permanent **Mark of Return** |
| **Mark grants** | *Sense* nearby ghosts (proximity cue, no visual) |
| **Ghost sight (visual)** | A rare Singularity-tier trait, **or** an item learned from the lore books |
| **Injuries** | Persist within a life, decay over real time, faster with Regeneration. **Respawn clears everything** |
| **Mortal wounds** | No natural decay; treatable only by rare abilities — or by spending a life for a fresh body |
| **Limbo** | Shared, generated, endless fog world with landmarks. No death. Occupants see and interact with each other |
| **Limbo comms** | Limbo-only chat, plus faint one-way whispers out to the living |
| **Ghost visits** | 5 min spectator in the living world, **15 min cooldown** |
| **First-join rolls** | 100% a trait · 25% a second · low chance Singularity-tier · 15% blessing · 15% curse. **Player is told everything** |
| **Authoring model** | Java classes for behaviour, **every number in YAML**, hot-reload via `/liminalis reload` |
| **Singularity mobs** | Vanilla bases, custom AI/attributes/particles/sounds, pack-swapped textures |
| **Ability unlocks** | Per-ability conditions, with Singularity drops as a universal accelerant |
| **Revival mechanic** | An expedition — living players travel somewhere dangerous to retrieve them |
| **Scale** | Under ~15 players → flat per-player JSON behind a storage interface |
| **Resource pack** | Required, server-enforced |
| **HUD** | Quiet by default. **Injury icons are the one always-on overlay.** Everything else via `/profile` |
| **Admin tooling** | A first-class system built in Phase 0 — `/liminalis` Brigadier tree, offline-capable, audited, with a subtree per subsystem |

---

## Architecture

### Two modules, one jar

```
liminalis/
  liminalis-core/     pure Java 21 — NO org.bukkit import, ever
  liminalis-plugin/   Paper adapter — listeners, commands, worldgen, entities, I/O
```

`core` holds every rule worth being sure about: life arithmetic, roll tables, the damage →
injury classifier, unlock-condition evaluation, the profile state machine. It has no server
dependency, so it is tested with plain JUnit 5 — no mock framework, no server, milliseconds
per run. The module boundary means **the compiler enforces the split**; discipline is not
required. `maven-shade-plugin` emits a single jar.

This is the one piece of ceremony added deliberately, and it is what makes "perfect" achievable:
the logic that decides whether a death costs you your last life is testable without launching
Minecraft.

### The Modifier framework — the spine

Traits, blessings, curses, injuries, mortal wounds, marks and abilities are all one thing:
something attached to a player that changes how they work.

```java
public interface Modifier {
    NamespacedKey key();
    Component displayName();
    Component description();
    void onAttach(LiminalisPlayer p);
    void onDetach(LiminalisPlayer p);
}
```

Behaviour comes from small capability interfaces a modifier opts into, rather than one fat
interface:

- `AttributeSource` — static attribute modifiers (Short → `SCALE`, mining → `BLOCK_BREAK_SPEED`, +hearts → `MAX_HEALTH`)
- `DynamicAttributeSource` — recomputed when a tracked variable changes. **This one primitive powers both Resilience and Coward**, and most future traits like them
- `DamageModifier` — hooks incoming/outgoing damage
- `Restriction` — vetoes actions (the "cannot wear Protection IV" class of curse downside)
- `Ticking` — periodic work, registered into one shared loop

**Hard rule: no modifier registers its own Bukkit listener and no modifier owns a `BukkitTask`.**
One central listener set and one shared tick loop dispatch to whatever is attached to the
affected player. At this scale it costs nothing and it keeps ordering deterministic.

### Persistence

`PlayerProfile` → `ProfileStore` interface → `JsonProfileStore` writing
`plugins/Liminalis/players/<uuid>.json` via Gson.

- **Atomic writes** — temp file then `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`
- Async saves on a single-threaded executor; synchronous flush on quit and on disable
- **Schema version field and a migration hook from day one** — non-negotiable for a season-long server
- Profile directory copied to `backups/<timestamp>/` on every server start
- Load happens async on `AsyncPlayerPreLoginEvent`, so nothing blocks the main thread on join

Hand-editable JSON matters here specifically because you assign abilities by hand.

### Supporting services

- **Config** — every tunable number in `config.yml`. `ConfigService` **validates on load and refuses to apply an invalid config**, keeping prior values and logging loudly, rather than silently defaulting a typo'd probability
- **Text** — all player-facing strings in `messages.yml`, rendered with MiniMessage. Lore-heavy server; tone should be editable without a rebuild

---

## Admin command system

You are the Creator: you hand-assign every ability, excuse technical deaths, and will need to
repair state on a server where a bad roll or a lag death is permanent. The admin tooling is a
core system, not an afterthought — built as a framework in Phase 0, with each phase adding its
own subtree as that subsystem lands.

Root `/liminalis` (alias `/lim`), built on `paper-plugin.yml` + Paper's Brigadier API via
`LifecycleEvents.COMMANDS`.

```
/liminalis reload [config|messages|all]
/liminalis profile <player>              full state dump
/liminalis debug <on|off>

lives         get|set|give|take <player> [n]
              pvpcounts <on|off>              the PvP-deaths-count toggle
limbo         send|revive <player> · list · tp · ghostreset <player>
trait         list · info <trait> · give|remove <player> <trait> · reroll <player>
boon          list · set <player> <blessing|curse> <id> · clear <player>
injury        list · info <injury> · give <player> <injury> · heal <player> [injury|all]
singularity   spawn <type> · forcewave · book <player> <1-5> · drops give <player> <n>
ability       list · set|clear <player> [ability] · tier <player> <set|up|down> [n]
              progress <player>
data          save <player|all> · inspect <player> · backup · reloadfile <player>
```

Eight properties that make it trustworthy rather than just present:

1. **Registry-backed argument types** — custom Brigadier `ArgumentType`s for trait, injury,
   boon, ability and mob IDs. Tab completion comes live from the registry, so a typo is
   impossible and you never have to remember an ID
2. **Offline players work everywhere** — every command loads the target's profile from disk,
   mutates and saves. You will be assigning abilities at 2am while they're asleep; this is
   not optional
3. **Audit log** — every mutation appends to `audit.log` with timestamp, actor, target and
   before → after. On a season-long server with hand-assigned abilities and manually excused
   deaths, "who gave them that" is a question you will actually ask
4. **Confirmation on destructive ops** — `lives set 0`, `trait reroll`, `boon clear` and
   `data reloadfile` need a second invocation within 10s. An accidental tab-complete should
   not erase someone's identity
5. **Reads are strictly separate from writes** — `get`/`list`/`info` never mutate, so they're
   safe to explore with
6. **Granular permissions** — `liminalis.admin.<subsystem>` under a `liminalis.admin.*`
   parent, so a trusted moderator can be given lives and injuries but not abilities
7. **Every mutation echoes before → after**, and optionally notifies the target
8. **`profile` and `data inspect` are the debugging entry points** — one human-readable, one
   raw JSON. When something looks wrong mid-season, these are where you start

---

## Build phases

Each phase is a shippable vertical slice, ends with a manual pass on a real 1.21.11 server,
and is not started until the previous one is verified.

### Phase 0 — Foundation
No player-visible features. Multi-module Maven build, `paper-plugin.yml`, main class.
`ConfigService` + `MessageService`. `PlayerProfile`, `ProfileStore`, `JsonProfileStore`,
`ProfileManager`. The `Modifier` framework and its central dispatch.
**The admin command framework in full** — Brigadier root, registry-backed argument types,
offline-profile access, audit log, confirmation guard, permission tree — plus the subtrees
that exist this early (`reload`, `profile`, `debug`, `data`). JUnit 5 harness in core.
A local Paper 1.21.11 test server for iteration. Design spec committed to the repo.

**Verify:** plugin enables cleanly · a profile file appears on join and survives restart ·
`/liminalis reload` works · `data inspect` on an **offline** player returns their JSON ·
a destructive command refuses without confirmation and writes to `audit.log` · `mvn test` green.

### Phase 1 — World rules
Smallest real slice, chosen to prove the whole pipeline end to end.
PvP damage ×0.5 (melee, projectiles, and configurable handling of player-attributed indirect
damage like TNT and pets) · food healing ×0.5 · Regeneration buffed.

**Verify:** core unit tests on the multipliers · in-game: hit a player, eat, drink a regen potion.

### Phase 2 — Lives, death & Limbo
Adds the **`lives` and `limbo` command subtrees**, including the `pvpcounts` toggle and
`lives give` for excusing technical deaths.
Life tracking and death classification · 3rd death → Limbo, persisted and **re-enforced on login** so a Limbo
player who logs in anywhere lands in Limbo · custom `ChunkGenerator` producing the endless fog
expanse with periodic landmarks · total invulnerability and containment in Limbo · Limbo-only
chat plus faint one-way whispers out · `/limbo visit` for the 5-minute ghost trip with a
persisted 15-minute cooldown · `Mark of Return` and its ghost-sense · **`limbo revive` as the
admin safety valve** until Phase 7 lands.

**Verify:** die 3× → Limbo · restart → still in Limbo · attempt every escape route (portals,
death, commands, world border) · ghost visit expires and returns you · admin revive yields
exactly 2 lives + mark.

### Phase 3 — Traits
Adds the **`trait` subtree** (`give`/`remove`/`reroll`/`info`, with live tab completion from
the registry).
Trait registry and the first-join roll · the `DynamicAttributeSource` primitive ·
starter roster spanning both scales (Short, faster mining, and similar, alongside Resilience
and Coward) · 1–2 Singularity-tier traits including **Deathsight** · `/profile` ·
the join message that tells a player exactly what they rolled.

**Verify:** roll-table distribution test over 100k iterations against configured rates ·
each trait's effect confirmed in-game.

### Phase 4 — Blessings & curses
Adds the **`boon` subtree**. Pure reuse of the modifier framework. 15%/15%, mutually exclusive · the `Restriction`
primitive · roster of ~5 blessings and ~5 curses, curses carrying the stronger upside.

**Verify:** distribution tests · every curse restriction confirmed in-game.

### Phase 5 — Injuries & mortal wounds
Adds the **`injury` subtree** — `injury give` in particular is how every injury gets tested
without hunting down a netherite axe.
`DamageDescriptor` in core (cause, amount, armour mitigation, resulting health fraction) →
classification into none / injury / mortal wound → selection from the matching pool.
Injury pools per damage category: slashing → bleeding, falling → sprain then broken legs,
fire → burns, explosion → concussion, piercing, crushing. Natural decay accelerated by
Regeneration; mortal wounds do not decay. **Pack-driven injury icon HUD.** Respawn clears
everything. Treatment hook defined but unused until abilities exist.

**Verify:** core test matrix across damage causes × amounts × armour tiers · in-game threshold
checks in leather vs netherite.

### Phase 6 — The Singularity
Adds the **`singularity` subtree** (`spawn`, `forcewave`, `book`, `drops give`).
Spawn scheduler at 50% per online player per 30 min with sane placement rules · 2–4 mob types
on vanilla bases with custom AI, attributes, particles, sounds and pack textures · 75% chance
to drop 1 of 5 lore books · the books written as **real, usable knowledge** — the revival book
carries the actual instructions and one book teaches the ghost-sight item · Singularity drops
introduced as the ability-unlock accelerant.

**Verify:** force-spawn command · drop-rate test over many iterations · book content reviewed
by you before it ships.

### Phase 7 — Revival: the expedition
Designed in full at this phase, once the books, Singularity and Limbo all exist and it can tie
them together rather than being guessed at now. Returns the player with 2 lives + Mark of Return.

**Verify:** the complete loop — die 3×, be retrieved, return in the correct state.

### Phase 8 — Abilities
Adds the **`ability` subtree** — this is the one you'll use most, and it must work on offline
players, since you'll be assigning abilities between sessions.
Tier system, per-ability `UnlockCondition` evaluation, Singularity-drop
acceleration, progress in `/profile`. **The Priest as reference implementation** — healing
others, holy damage against undead, and mortal-wound treatment at a high tier. Then one
ability at a time as players request them.

**Verify:** unlock-condition unit tests · Priest verified end to end including treating a
mortal wound.

### Phase 9 — The boss
Deferred deliberately. Designed once the world has matured and we know what the group can do.

---

## Engineering rules that hold across every phase

1. `liminalis-core` never imports `org.bukkit` — the module boundary enforces it
2. Core logic is written test-first
3. No per-modifier listeners, no per-modifier tasks
4. Every number lives in config, is validated on load, and hot-reloads
5. Every player-facing string lives in `messages.yml`
6. Profiles are versioned, written atomically, and backed up on start
7. **Every subsystem ships with its admin subtree in the same phase** — offline-capable,
   audited, tab-completed from its own registry. A system you can't inspect or repair from
   in-game isn't finished
8. A phase is not done until it has been manually verified on a real 1.21.11 server

---

## Verification

- `mvn clean verify` at the repo root — core unit tests must be green
- Local Paper 1.21.11 test server, jar dropped into `plugins/`, launched with the required pack
- Per-phase manual checklists as listed above
- Probability-driven systems (rolls, drops, injury chances) get statistical tests over large
  iteration counts, not single-run spot checks
- **The admin subtrees are the primary manual-test lever** — `injury give`, `singularity spawn`,
  `trait give`, `limbo send` mean every feature can be exercised on demand rather than waiting
  for a 30-minute spawn roll or a lucky netherite crit. Building them in Phase 0 pays for
  itself by Phase 2

---

## Open items, to settle at the phase that needs them

- Maven `groupId` — defaulting to `com.liminalis` unless you want otherwise
- Resource pack repo, hosting and zip/sha1 build step — first hard requirement is the Phase 5 injury HUD
- Exact trait / blessing / curse roster — drafted per phase for your approval
- Whether the ghost-sight item is crafted or found
- Boss design (Phase 9)
