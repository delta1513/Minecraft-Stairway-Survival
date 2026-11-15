---
title: Plot Setup
date: 2026-11-14
---

# Plot Setup

* Every plot is a 3×3 set of land (x-by-z, with unlimited range in the y)
* Every game will consist of 6 plots which are arranged around a circle with radius of 20 blocks with the center at the world spawn point
* To place plots, simply plot a circle and assign the center of a plot as the block closest to the point on the circle every 60-degrees.
* Every plot has a chest. In the chest we will have:
    
    * 4 Saplings
    * 4 Bonemeal
    * 4 Seeds
    * 4 Beds
    * 4 Loaves of bread
    * A bucket of water
* Each plot will use some sort of effect to demonstrate the borders of the plot. Whatever this is, it must be mostly transparent, and also visible by other players in other plots.
* The borders of the plots are not impassible. When players pass through the first block outside of this wall, apply Poison I, Blindness, Nausea, Slowness III, Mining Fatigue V, and Hunger I for 1 minute.
* When players go two or more blocks outside the border, apply Wither V permanently.
* Plots are safe zones: any position that is not inside a plot or the 1-block ring around one counts as "two or more blocks outside", wherever it is on the map.
* The gameplay area is 101×101 blocks centered on the spawn point. This border must prevent people from traversing it. This border should also have a transparent effect to denote its edges.

# Operations

1. Get everyone into the server. Everyone will spawn inside of the gameplay area in adventure mode until the build phase begins.
2. The build phase begins when everyone is assigned a plot (there are 6 plots, each with a differently named colour. Players will choose their plot by typing a command like “assignme yellow” into the chat for example).
    
    1. Note: nothing happens when you type “assignme”, it will simply note you down as being apart of that plot. Then it will teleport you there when the game starts.
3. The build phase will initiate its countdown when everyone is assigned a plot.
4. In the countdown, all players are teleported and locked into the center of their plot until the countdown ends.
5. When the countdown ends, the players will have all effects removed, be able to move freely within their plot, and have their health and hunger replenished to maximum.
6. The game rules will remain as-is until someone reaches Y=319. When this happens, the game will migrate to the survival phase.
7. When the survival phase starts, all the borders of each plot are removed so that players can move about freely in the gameplay area only. The endgame timer will also begin counting, and any players that die during this phase are permanently disqualified (they will respawn in spectator mode).
8. Every minute, two y-levels in the gameplay area (starting from the void at -64 and moving upwards) will vanish, and a negative status effect will be applied globally from the list below.

# Death status effects

* Fill every space in your inventory with feathers
* Apply blindness for 3 minutes
* Apply nausea for 3 minutes
* Spawn a water block above the player
* Change the weather to a thunderstorm
* Give the player Jump Boost X (no feather falling) for 1 minute

# Survival phase random effects

Other than periodically removing a layer from the world, these random effects will fire:

* Set time to night (permanently)
* Set the weather to thunderstorming
* Remove all wooden planks from the gameplay area
* Remove all ores from the gameplay area
* Spawn a zombie and a skeleton next to each player
* Remove all other-stones like diorite and andesite
* Remove all grass blocks (not dirt)
* Spawn 10 silverfish around each player
* Replace all leaves with chickens
* Replace all flowers with creepers
* Replace all water with lava
* Afflict everyone with hunger III and nausea for 1 minute
* Afflict everyone with Jump Boost X (no feather falling) for 1 minute