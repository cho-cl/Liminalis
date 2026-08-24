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

**Phases 0-3 complete.**

**Phase 1 - world rules.** PvP damage halved, food healing halved, Regeneration buffed.
Damage is traced through projectiles, tamed pets and primed TNT to the player who really
caused it, since halving only melee would move fighting to bows and wolves rather than reduce
it. Each indirect source can be excluded in config.

**Phase 2 - lives, death and Limbo.** Everyone gets three lives. The third death sends them to
Limbo: an endless, treeless pale garden with no caves, structures or living things. Nothing
there can hurt you, hunger never drops, and there is no way out - portals, teleports, other
plugins' warps and logging back in elsewhere are all refused. The dead talk only to each
other, though what they say occasionally bleeds through to the living worn down to the shape
of a sentence. Once every fifteen minutes they may spend five minutes among the living as an
unseen spectator.

**Phase 3 - traits.** Everyone is rolled a trait on first join, a quarter get a second, and a
few percent reach the Singularity tier. Small ones (Short, Swift Hands, Ironbound, Deep Lungs)
sit alongside ones that change a fight (Resilience, Coward) and two that change what you can
perceive (Deathsight, Stillness).

**Phase 4 - blessings and curses.** Fifteen percent of players are blessed, fifteen percent
cursed, and the two are exclusive slices of one roll rather than two rolls in sequence - which
is the difference between a 15% curse rate and a 12.75% one. A blessing is a straight gift. A
curse is a bargain: a bigger gift than any blessing, paid for with something you would rather
keep. Hollow gives three extra hearts and will not let heavy protection stay on you; Unshod
makes you fast and barefoot forever.

**Phase 5 - injuries and mortal wounds.** Large damage wounds you; massive damage maims you.
Severity is judged on damage *after* armour as a fraction of your own maximum health, so heavy
protection genuinely protects against losing an arm, and a player with extra hearts is not
disproportionately hard to hurt. Wounds match their cause - a sword makes you bleed, a long
fall sprains an ankle, an explosion concusses you. Ordinary injuries fade with time and fade
faster under Regeneration. Mortal wounds never fade, and until a healing ability exists the
only cure is to spend a life and get a new body.

**Phase 6 - the Singularity.** Every thirty minutes, each online player is rolled separately
for a creature - so the world gets busier as more people are in it, rather than thinner. Three
shapes, and no single answer that beats all of them: one that will not stop coming, one that
ignores walls, and one that will not let you close the distance. Killing one yields residue
(the universal accelerant for ability unlocks) and, three times in four, one of five books.

The books are the only source of knowledge about any of this, and they are physical written
books rather than an unlockable codex - so they can be traded, hoarded, copied at a lectern
and argued over. The knowledge spreading socially is the point.

**Phase 7 - revival.** Nothing done from the living world reaches into Limbo, so someone has
to go. A Threshold Stone - eight Singularity residue around an ender pearl, single use - opens
a way. The rescuer crosses into the grey, finds the lost in fog with no landmarks, takes their
hand, and walks back to the light they arrived at. The way in is the only way out, and losing
track of it is the entire difficulty of the trip.

Run the clock out and the grey takes a life for letting you go. Run it out with nothing left
to give and it keeps you - the only way in the game to be lost without dying. Disconnecting
mid-crossing is free: the risk should come from the grey, not the network.

Every number across all phases lives in `config.yml` and is read at the moment it applies, so
a rebalance takes effect on `/liminalis reload` with no restart.

*Known gaps, all deliberate:* end crystals and player-ignited
creepers are not attributed as player damage; the third death drops your inventory
vanilla-style; re-enabling Limbo decorations would only affect newly generated chunks; mortal
wounds have no treatment yet beyond dying; and the injury HUD and Singularity textures both
wait on a resource pack that has not been made.

Later phases - abilities and the boss - are described in the design doc and built one at a time, each verified on a real server before
the next.
