# Stairway Survival

A PaperMC plugin for a minigame. Teams are trapped in 3×3 plots and race to climb to Y=319. When someone gets there, the world collapses from the bottom up, and the last team standing wins.

- Player rules: `Minecraft Stairway Survival Challenge.md`
- Implementation spec: `PLAN.md`

## Commands

Colours are `red`, `orange`, `yellow`, `lime`, `blue` and `purple`.

### Player commands (permission `stairway.play`, everyone by default)

| Command | When | What it does |
|---|---|---|
| `/assignme <colour>` | Lobby | Joins that colour's team. You can't switch teams once you've joined |

### Admin commands (permission `stairway.admin`, ops by default)

| Command | When | What it does |
|---|---|---|
| `/stairway status` | Any time | Shows the phase, the teams, how many players are still in, and the next layer to collapse |
| `/stairway assign <player> <colour>` | Lobby | Puts a player on a team, or moves them to a different one |
| `/stairway start` | Lobby | Starts the countdown now. Anyone without a team becomes a spectator |
| `/stairway survival` | Build phase | Jumps straight to the survival phase (handy for testing) |
| `/stairway disqualify <player>` | Build or survival phase | Disqualifies a player. This also works on offline players |
| `/stairway reset` | Any time | Ends the game and goes back to the lobby, clearing all teams. The world is not restored |
| `/stairway reloadmessages` | Any time | Reloads `plugins/StairwaySurvival/messages.json` |

## Random effect likelihood

Two sections in `config.yml` control how likely each random effect is. Each effect has an integer weight, and
its probability is `weight / sum of all weights` in that section. A weight of `0` disables the effect. Keys you
leave out default to `1`, and at least one weight in each section must be above `0`. Keys are not case-sensitive.

- `death-penalty-weights`: the six build-phase death penalties (`FEATHERS`, `BLINDNESS`, `NAUSEA`, `WATER_ABOVE`, `THUNDERSTORM`, `JUMP_BOOST_X`).
- `survival-effect-weights`: the 13 survival-phase effects (`PERMANENT_NIGHT` ... `JUMP_BOOST_X`, see the bundled `config.yml`).

Example: with `NAUSEA: 5`, `FEATHERS: 0` and the other four death penalties left at `1`, the sum is 9, so
nausea is picked 5/9 (about 56%) of the time, feathers never, and each other penalty 1/9 (about 11%).

## Testing in a real game

You need **Java 25**, because Paper 26.3 requires it.

### 1. Build the plugin

```bash
./gradlew build
```

The jar is written to `build/libs/stairway-survival-<version>.jar`.

### 2. Set up a Paper server

1. Download Paper 26.3 from https://papermc.io/downloads/paper into an empty folder.
2. Create a `plugins/` folder there and copy the plugin jar into it.
3. Run the server once. Then set `eula=true` in `eula.txt` and start it again:

   ```bash
   java -jar paper-26.3-*.jar --nogui
   ```

4. Give yourself op in the server console, so you can use the admin commands: `op <yourname>`.

### 3. Testing on your own

- By default the game needs 2 teams to start. Either set `min-teams: 1` in `plugins/StairwaySurvival/config.yml` and restart, or use a second account.
- To skip the climb, use `/stairway survival`. Setting `layer-interval-seconds` to something like `10` makes the collapse quick to watch.
- Chat messages live in `plugins/StairwaySurvival/messages.json`. After editing it, run `/stairway reloadmessages`.

### 4. What to check

| Step | Expected |
|---|---|
| Join | Adventure mode at spawn. A reminder to use `/assignme` appears every 30–60 s |
| `/assignme red` | Countdown message, then you're teleported to your plot and frozen, then switched to survival |
| Plot | A chest with the starter kit and a red particle border |
| Step 1 block out | A chat message, and the warning effects while you stay in the ring (they wear off 3 s after you leave) |
| Step 2 blocks out | A chat message, then Wither X and death almost straight away |
| Die with no bed set | Disqualified (spectator) |
| Place a bed, right-click it, then die | Respawn at the bed, a "spin the wheel" message, then a random penalty 5 s later |
| `/stairway survival` | A survival-start message and a boss bar. Each interval the bottom two layers vanish, then a wheel spin, then an effect 5 s later |
| `/stairway status` | Shows the phase, teams and next layer |
| `/stairway reset` | Back to the lobby. The world isn't restored, so use a fresh world for each test |

For a fresh world, stop the server and delete the `world*` folders.
