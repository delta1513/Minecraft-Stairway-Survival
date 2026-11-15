# PLAN.md — Stairway Survival (PaperMC plugin)

This is the implementation plan. The design sources are `Minecraft Stairway Survival Challenge.md` (the public rules) and `Minecraft Stairway Survival Challenge - internal document.md` (the internal spec). If those disagree with this file, this file wins. Values marked **(default)** were picked during planning; they go in `config.yml` and can be changed after playtesting.

---

## 1. Tech stack

| Item | Value |
|---|---|
| Server | Paper **26.3** (latest stable as of 2026-11-15) |
| Java | **25** (minimum Paper 26.3 requires) |
| Build | Gradle (Kotlin DSL), `compileOnly("io.papermc.paper:paper-api:26.3.build.+")`, repo `https://repo.papermc.io/repository/maven-public/` |
| Commands | Paper Brigadier command API (registered through `LifecycleEvents.COMMANDS`) |
| Tests | JUnit 5 for pure logic (geometry, zone maths, state transitions). Gameplay is tested by hand with the checklist in §12. |

Goal: drop the jar into any Paper 26.3 server with **any world seed** and run a game. The plugin never generates or resets worlds.

---

## 2. Glossary

- **Game world**: the overworld named in config (default `world`).
- **Spawn**: `gameWorld.getSpawnLocation()`, as block coordinates `(sx, sz)`.
- **Gameplay area**: block columns with `sx-50 ≤ x ≤ sx+50` and `sz-50 ≤ z ≤ sz+50` (101×101), at every Y.
- **Plot**: a 3×3 block column covering every Y (−64 to 319). There are 6 plots, and each one belongs to a colour/team.
- **Team**: one colour. A team has 1 or more players. Players cannot switch teams.
- **Active team / plot**: a colour that has at least one assigned player when the countdown starts. Plots without a team are not prepared and do not count as plots (no chest, no border, no safe zone).
- **Alive participant**: an assigned player who has not been disqualified. Online or offline makes no difference.
- **Disqualified**: permanently out of the game and put in spectator mode.

---

## 3. Geometry

### 3.1 Plot centres
For `i = 0..5`, `θ = i × 60°`:

```
cx = sx + round(20 · cos θ)
cz = sz + round(20 · sin θ)
```

`round` here is round-half-up, `floor(v + 0.5)`. That gives the block whose centre is closest to the point on the circle, because the circle is centred on the centre of the spawn block. The resulting offsets, with +x = east and +z = south:

| i | Colour | Offset (dx, dz) | Bed material |
|---|---|---|---|
| 0 | red | (20, 0) | `RED_BED` |
| 1 | orange | (10, 17) | `ORANGE_BED` |
| 2 | yellow | (−10, 17) | `YELLOW_BED` |
| 3 | lime | (−20, 0) | `LIME_BED` |
| 4 | blue | (−10, −17) | `BLUE_BED` |
| 5 | purple | (10, −17) | `PURPLE_BED` |

The colour names are **(default)**. The colour names are the command arguments.

### 3.2 Zone of a block column
Take a player's block column `(bx, bz) = (floor(x), floor(z))` and an active plot `p`:

```
d(p) = max(|bx - cx|, |bz - cz|) - 1     // Chebyshev distance from the plot's edge
d    = min over all active plots of d(p)
```

| d | Zone |
|---|---|
| ≤ 0 | `INSIDE`: inside some active plot (own team's or another team's) |
| 1 | `RING`: the first block outside a plot |
| ≥ 2 | `WILD`: further out |

Plots are 20 blocks apart, so no two plots' rings overlap.

### 3.3 Gameplay area wall
Use the game world's vanilla world border: centre `(sx + 0.5, sz + 0.5)`, size `101`. That covers exactly the 101×101 block area, blocks players from walking through, and draws the vanilla transparent border texture. Set it when the plugin enables and keep it for the whole game. As a fallback, an alive participant whose block column is ever outside the gameplay area is disqualified (§8).

---

## 4. Game state machine

```
LOBBY ──(all online assigned & ≥ min-teams)──▶ COUNTDOWN ──(timer)──▶ BUILD ──(alive player Y ≥ 319)──▶ SURVIVAL
   ▲                                                                    │                                  │
   └──────────────────────── /stairway reset ◀──── ENDED ◀── (≤1 team left) ──────────────────────────────┘
```

All state lives in memory. The game does not survive a server restart (non-goal).

### 4.1 LOBBY
- On enable: resolve the game world, compute the plots, set the world border.
- Every joining player is teleported to the spawn surface (`highestBlockY + 1`) and set to **adventure** mode. PvP is not blocked.
- `/assignme <colour>` (tab-completes the 6 colours):
  - Works only in LOBBY.
  - If the player is already assigned: reply "You are already on <colour>", and change nothing.
  - Otherwise: record the assignment and broadcast "<player> joined <colour>". Nothing else happens.
- After every assignment, join and quit, check the start condition. **Every online player is assigned** and **the assigned players span ≥ `min-teams` distinct colours** (default **2**). If it holds, go to COUNTDOWN.

### 4.2 COUNTDOWN (`countdown-seconds`, default **10**)
- Prepare each active plot (§5).
- Teleport every online assigned player to their plot's spawn point (§5).
- Lock the players: cancel any `PlayerMoveEvent` that changes block position (looking around is still allowed). Make them invulnerable.
- Show the countdown in a title or the action bar each second.
- An unassigned player who joins now becomes a spectator (§8.3).
- When the countdown ends, go to BUILD.

### 4.3 BUILD
On entry, for every online assigned player:
- Unlock, remove invulnerability, set **survival** mode.
- Clear all potion effects. Clear the inventory.
- Set health to max, food to 20 and saturation to 20.

An assigned player who first joins during BUILD gets the same treatment and is teleported to their plot spawn. A returning player keeps their vanilla location and state.

Running during BUILD:
- Zone enforcement (§6).
- Plot border particles (§7).
- Build-phase death rules (§9.1).
- **Survival trigger**: every tick, if any online alive participant in survival mode in the game world has `location.y ≥ 319` (`survival-trigger-y`), broadcast "<player> reached Y=319! The survival phase begins." and go to SURVIVAL.

### 4.4 SURVIVAL
On entry:
- Stop zone enforcement and plot particles.
- Existing potion effects are left alone. Wither V stays, because it is permanent.
- Start the layer clock: `nextLayerY = -64`.
- Show a boss bar to everyone: `Floor collapsing — Y < {nextLayerY} gone — next in {s}s`.

Every `layer-interval-seconds` (default **60**), starting one interval after entry:
1. Remove layers `nextLayerY` and `nextLayerY + 1` across the gameplay area (§10.1). Then `nextLayerY += 2`.
2. Fire one survival random effect (§10.2).
3. When `nextLayerY > 319`, stop the clock. The arena is gone. The plan doesn't handle what happens next (ties etc. are a non-goal).

Any death in SURVIVAL disqualifies the player (§9.2).

### 4.5 ENDED
- Triggered whenever ≤ 1 team still has an alive participant, checked after every disqualification. This can happen during BUILD as well.
- Broadcast "<colour> wins!" with a title to everyone. Stop all tasks. Everyone stays in their current mode.
- `/stairway reset` returns to LOBBY: it clears assignments, puts all online players in adventure at spawn, and resets the world border. The world itself is **not** restored.

---

## 5. Plot preparation (at COUNTDOWN start, active plots only)

- Add a plugin chunk ticket (`Chunk#addPluginChunkTicket`) to every chunk that overlaps the gameplay area, and keep it until ENDED/reset. The area is about 7×7 chunks.
- **Surface Y** of a column = `world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES)`.
- **Plot spawn point** = `(cx + 0.5, surfaceY(cx, cz) + 1, cz + 0.5)`.
- **Chest**: one per plot, whatever the team size. It goes in the north-west column `(cx - 1, cz - 1)` at `surfaceY + 1` of that column, and replaces whatever block is there. Contents:

| Item | Amount |
|---|---|
| `OAK_SAPLING` **(default)** | 4 |
| `BONE_MEAL` | 4 |
| `WHEAT_SEEDS` | 4 |
| Team-colour bed (§3.1) | 4 |
| `BREAD` | 4 |
| `WATER_BUCKET` | 1 |

The kit is configurable as a material→amount list. The bed entry is written as `TEAM_BED`.

---

## 6. Zone enforcement (BUILD only)

A task runs every tick (`zone-check-interval-ticks`, default **1**). For each online alive participant in survival mode, it computes the zone (§3.2) and compares it with the zone stored for that player on the last check:

| Transition into | Action |
|---|---|
| `RING` (from INSIDE or WILD, or by teleport) | Apply for **60 s**: Poison I, Blindness I, Nausea I, Slowness III, Mining Fatigue V, Hunger I. Amplifier = level − 1. |
| `WILD` | Apply **Wither V** (amplifier 4) with **infinite** duration (`PotionEffect.INFINITE_DURATION`). It is **never removed by the plugin**. Only death clears it. |
| `INSIDE` | Nothing. Ring effects keep running out their 60 s, and Wither stays. |

- The ring effects are applied once per entry, not refreshed every tick. Going in and out of the ring applies them again.
- Ender pearls need no special code. Landing `INSIDE` any active plot triggers nothing, and landing in RING or WILD is handled by the table above.
- Wither V kills in about 2 s (§13), so WILD effectively means death and then the bed rules in §9.1.

---

## 7. Plot border visuals (BUILD only)

The visuals have to be mostly transparent and visible to players in other plots. Use **coloured dust particles**:
- The task runs every `particle-interval-ticks` (default **10**). For each online player (including spectators), and each active plot within `particle-view-distance` (default **64**) blocks of them horizontally:
  - Draw the 4 vertical faces of the plot boundary. That is the square from `cx - 1` to `cx + 2` (block-edge coordinates) on x and z.
  - Draw points every 0.5 blocks along the edges and every 1 block in Y, from `playerY - 10` to `playerY + 10`, clamped to −64..320.
- Use `Particle.DUST` with `DustOptions(teamColour, 1.0f)`. Send with `player.spawnParticle(...)` so each viewer only gets particles near their own height. The plot is effectively infinitely tall, so this keeps the particle count bounded.

---

## 8. Disqualification

`disqualify(player, reason)`:
1. Mark the player disqualified and set **spectator** mode.
2. If the player is not in the game world, teleport them to the spawn surface in the game world.
3. Broadcast "<player> was disqualified (<reason>)".
4. Run the win check (§4.5).

### 8.1 Triggers (in BUILD and SURVIVAL)
| Trigger | Detection |
|---|---|
| Left the overworld (Nether/End/any other world) | `PlayerChangedWorldEvent` where the new world ≠ game world |
| Outside the gameplay area | The per-tick check finds the block column outside 101×101 |
| No bed on respawn in BUILD | §9.1 |
| Any death in SURVIVAL | §9.2 |
| Admin | `/stairway disqualify <player>` |

### 8.2 Offline players
Offline players are **never** disqualified automatically. They still count for their team. An admin can disqualify them manually.

### 8.3 Unassigned players while a game is on
Any unassigned player who is online when COUNTDOWN starts, or who joins later, is set to spectator. They are not disqualified (they were never in the game).

---

## 9. Death handling

`keepInventory` is **off**: the plugin sets the gamerule to false on enable. Items drop as in vanilla.

### 9.1 BUILD phase
In `PlayerRespawnEvent` for an alive participant:
- If `event.isBedSpawn()` is true, respawn at the bed as normal. **One tick later**, apply one death penalty picked uniformly at random from the list below.
- Otherwise (no bed, or the bed is broken or obstructed), let the respawn happen and then call `disqualify(player, "no bed")`. This catches the respawn-at-world-spawn death loop.

Death penalties:

| # | Penalty | Implementation |
|---|---|---|
| 1 | Inventory full of feathers | Fill every empty slot of the 36 storage slots with 64 `FEATHER` |
| 2 | Blindness 3 min | Blindness I, 180 s |
| 3 | Nausea 3 min | Nausea I, 180 s |
| 4 | Water above the player | If the block at `eyeLocation.block + (0,1,0)` is air or replaceable, set it to a `WATER` source |
| 5 | Thunderstorm | Same as survival effect "thunderstorm" (§10.2) |
| 6 | Jump Boost X 1 min | Jump Boost amplifier 9, 60 s. No fall-damage protection |

### 9.2 SURVIVAL phase
`PlayerDeathEvent` for an alive participant → `disqualify(player, "died")`. They respawn in spectator mode.

### 9.3 LOBBY / COUNTDOWN
Players are invulnerable during COUNTDOWN. A death in LOBBY just respawns them at spawn in adventure mode.

---

## 10. Survival phase world changes

**Region** = the gameplay area columns × Y from `nextLayerY` (already-removed layers are skipped) to 319.

Block edits use `block.setType(material, false)` (no physics). They run on the main thread and are spread over ticks with a budget of `block-ops-per-tick` (default **20 000**) block reads or writes. Every bulk edit goes through one shared FIFO job queue, so edits never overlap.

Implementation hint: for whole-region scans, take a `ChunkSnapshot` of each of the ~49 chunks. Scan the snapshots off the main thread to collect matching positions, then apply the changes on the main thread within the budget.

### 10.1 Layer removal
Set every block in the 101×101 area at Y = `nextLayerY` and `nextLayerY + 1` to `AIR`. Bedrock is included. That's 20 402 blocks per minute. Block entities such as chests disappear without dropping items. Entities are left alone.

### 10.2 Random effects (one per interval, uniform, with replacement)

| # | Effect | Implementation |
|---|---|---|
| 1 | Permanent night | `setTime(18000)` and turn off the daylight-cycle gamerule (§13) |
| 2 | Thunderstorm | `setStorm(true)`, `setThundering(true)`, weather and thunder duration `thunder-duration-seconds` (default **300**) |
| 3 | Remove planks | Region: every block in `Tag.PLANKS` → `AIR` |
| 4 | Remove ores | Region: every material whose name ends in `_ORE` (covers the deepslate variants) → `AIR` |
| 5 | Zombie + skeleton | For each online alive participant: spawn 1 `ZOMBIE` and 1 `SKELETON` within 2 blocks, on air above a solid block. Fall back to the player's location |
| 6 | Remove other stones | Region: `GRANITE`, `DIORITE`, `ANDESITE`, `TUFF` **(default list)** → `AIR` |
| 7 | Remove grass | Region: `GRASS_BLOCK` → `AIR` (dirt is untouched) |
| 8 | Silverfish | For each online alive participant: spawn 10 `SILVERFISH` within 3 blocks |
| 9 | Leaves → chickens | Region: every block in `Tag.LEAVES` → `AIR`, plus one `CHICKEN` at that block's centre. **No cap.** |
| 10 | Flowers → creepers | Region: every block in `Tag.FLOWERS` → `AIR`. One `CREEPER` per plant: for two-tall flowers, spawn only at the lower half. **No cap.** |
| 11 | Water → lava | Region: every `WATER` and `BUBBLE_COLUMN` block → `LAVA`. Waterlogged blocks are untouched |
| 12 | Hunger + nausea | All online alive participants: Hunger III + Nausea I, 60 s |
| 13 | Jump Boost X | All online alive participants: Jump Boost amplifier 9, 60 s |

Broadcast the effect name in chat when it fires.

Entity spawns from effects 9 and 10 are capped per tick at `entity-spawns-per-tick` (default **200**). This spreads them over time: every block still gets its mob.

---

## 11. Commands, permissions, config

### 11.1 Commands
| Command | Permission | Phase | Effect |
|---|---|---|---|
| `/assignme <colour>` | `stairway.play` (default: everyone) | LOBBY | Join a team (§4.1) |
| `/stairway status` | `stairway.admin` (op) | any | Phase, teams, alive counts, next layer |
| `/stairway assign <player> <colour>` | admin | LOBBY | Assign or override a player's team |
| `/stairway start` | admin | LOBBY | Force COUNTDOWN. Unassigned players become spectators |
| `/stairway survival` | admin | BUILD | Force SURVIVAL (for testing) |
| `/stairway disqualify <player>` | admin | BUILD/SURVIVAL | §8 |
| `/stairway reset` | admin | any | §4.5 |

### 11.2 `config.yml` (defaults)
```yaml
game-world: world
min-teams: 2
countdown-seconds: 10
survival-trigger-y: 319
layer-interval-seconds: 60
layers-per-interval: 2
gameplay-area-size: 101
plot-circle-radius: 20
zone-check-interval-ticks: 1
particle-interval-ticks: 10
particle-view-distance: 64
thunder-duration-seconds: 300
block-ops-per-tick: 20000
entity-spawns-per-tick: 200
other-stones: [GRANITE, DIORITE, ANDESITE, TUFF]
colours: [red, orange, yellow, lime, blue, purple]   # index = plot i in §3.1
starter-kit:
  OAK_SAPLING: 4
  BONE_MEAL: 4
  WHEAT_SEEDS: 4
  TEAM_BED: 4
  BREAD: 4
  WATER_BUCKET: 1
```

---

## 12. Suggested code layout and milestones

```
src/main/java/<pkg>/
  StairwayPlugin.java        // enable/disable, wiring
  config/GameConfig.java     // typed config
  arena/Arena.java           // spawn, plots, area bounds, zone maths (pure, unit-tested)
  arena/Plot.java
  game/GameManager.java      // state machine, teams, disqualify, win check
  game/Phase.java
  listener/*.java            // join/quit, move-lock, death/respawn, world change
  task/ZoneTask.java, ParticleTask.java, SurvivalClock.java
  world/BlockJobQueue.java   // budgeted bulk edits
  effects/DeathPenalty.java, SurvivalEffect.java   // enum + registry
  command/Commands.java
```

Milestones, each one manually playable:
1. Project skeleton, config, `Arena` geometry plus unit tests for §3.1 offsets and the §3.2 zones.
2. LOBBY plus `/assignme`, start condition, COUNTDOWN, plot prep, BUILD entry.
3. Zone enforcement, border particles, world border.
4. Death/respawn rules, disqualification, win check, world-change detection.
5. SURVIVAL: trigger, layer clock, block job queue, boss bar.
6. All 13 survival effects and 6 death penalties.
7. Admin commands and `/stairway reset`.

### Manual test checklist
- 2 players, 2 colours: the countdown starts only after both are assigned. An unassigned third player blocks the start. That player becomes a spectator after `/stairway start`.
- Step 1 block out of a plot: the 6 ring effects for 60 s. Step 2 out: Wither V, death.
- Die with a bed set: respawn in the plot with one penalty. Die with no bed: disqualified.
- Enter a Nether portal: disqualified.
- Pillar up to Y=319: SURVIVAL starts, particles stop, the boss bar appears. After 60 s the layers at Y −64/−63 are air across 101×101 and one effect fires.
- Last team alive → win broadcast.

---

## 13. Facts to verify against authoritative sources (next step)

These assumptions need a cited source before or during implementation:
- The overworld build limit is still Y −64..319 in 26.3.
- Wither damage rate by amplifier, which is the basis for "Wither V ≈ 2 s to kill".
- Bed respawn rules: right-clicking a bed sets spawn during the day, and an obstructed or missing bed falls back to world spawn. Also how `PlayerRespawnEvent#isBedSpawn` behaves in that case.
- The current names of the daylight-cycle and keepInventory gamerules in the 26.x API. Gamerule identifiers were renamed in recent versions.
- The effect type constants in the 26.x API (`NAUSEA`, `MINING_FATIGUE`, `SLOWNESS`, `JUMP_BOOST`, …) and `PotionEffect.INFINITE_DURATION`.
- The world border blocks walking and ender pearls through it.
- What `Tag.FLOWERS` / `Tag.LEAVES` contain in 26.3, e.g. whether flowering azalea or cherry leaves fall in both.
- `setType(AIR, false)` on a container removes its contents without dropping them.
- The Brigadier command registration API and the plugin descriptor (`plugin.yml` vs `paper-plugin.yml`) for 26.3.

## 14. Non-goals
- Persisting a game across server restarts.
- Resetting or regenerating the world after a game.
- Tie-breaking when the last teams die at the same time, or anything after the arena is fully removed.
- Running more than one game at a time.
- Balance tuning (to be done after playtesting).
