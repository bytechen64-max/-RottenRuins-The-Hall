# Changelog
> Minecraft Version: 1.20.1 | Mod Loader: Forge

## [0.3.0] - 2026-9-27

### Added
- **New Mobs**
  - Ulcerated Scout
  - Ulcerated Pursuer
  - Ulcerated Monolith
  - Collapsar
- **New Entities**
  - Black Hole
- **New Blocks**
  - Flesh Rift
- **New Dimension**
  - Carrion Marrow
- **Miscellaneous**
  - Shockwave shader optimization
  - Hall mobs can now infect their kills
  - New mob data: `Threat Points`
  - Black hole distortion shader

### Fixed
- Fixed block behaviors for Hall Grass, Hall Flower, Hall Ash Cactus, etc.
- Fixed a model loading crash

## [1.0.0] - 2026-11-21
> Minecraft Version: 1.20.1 | Mod Loader: Forge 47.4.20+
> Dependencies: Cloth Config, GeckoLib, Curios 5.4+ (all mandatory) | Main network protocol: **4** (client and server must match)
>
> 1.0.0 folds the Hall development line into this mod: namespace, Java packages, assets and config
> directories are unified as `rotten_ruins`, the infcore infection core is now built from source
> (the standalone `infcore-*.jar` is no longer needed), and the bilingual lang tables hold 650 keys.

### Added

#### Magic
- **Seven schools**: Light, Fire, Water, Nature, Death, Void, Mysterious. A spell may combine schools;
  their bonuses are averaged.
- **16 spells**: Fireball, Tidal Pulse, Briar Snare, Sprint, Freezing Rain, Flame Cleave, Holy Wings,
  Shield Ward, Soul Drink, Purifying Light, Arcane Step, Suppression Field, Black Hole, Spear of Judgment,
  Olympiad Cube, Bark Armor. Three use modes: immediate, charge and keep. Releasing below the minimum
  charge ratio voids the cast; a valid charge scales the effect.
- **9 wands**: empty, common and one per school. Grades are Common / Epic / Legendary and add damage to
  spells of the matching school.
- **Spell wheel (`R`)**: 3 pages × 8 sockets. Aim with the mouse and release to cast; right-click / `Esc`
  cancels. Immediate spells fire on release, charge spells begin charging after a 120 ms dwell, and keep
  spells start after 250 ms and collapse into a sustain bar at the bottom.
  Holding `R` swallows vanilla "swap offhand"; the key is rebindable.
- **Spell loadout (`V`)**: drag active spells onto sockets, right-click to unbind, `SAVE` writes the page.
  Passive spells cannot be placed on the wheel.
- **Spell constellation (`M`)**: 5 slots by default (`magic_slots` expands them, cap 64), slots float on a
  constellation polyline, and the right side lists only spells in your inventory.
  Dragging, moving and selecting are validated server-side before being pushed back.
- **Wand HUD**: animated mana rings, a spell picker popup, a show/hide toggle and drag positioning.
- **Attributes**: max mana, mana regeneration, mana restored, spell power, seven school powers,
  cooldown reduction (capped at 50%) and magic slots. All values are resolved through `MagicStats`.
- **Mana and cooldowns**: 1000 starting mana, +1 per second, kills restore mana by the victim's max health;
  spells with the same item share one cooldown; respawning no longer loses slots or mana.
- **Trinkets**: Curios is a hard dependency; trinkets declare schools, matching-school checks and tooltip lines.
- **Enchantment**: Mana Efficiency.
- **Spell loot**: Fireball, Tidal Pulse, Briar Snare and Sprint are added to piglin bastion, buried treasure,
  jungle temple and desert temple chests at 30% / 50%. Implemented as loot modifiers, so data packs can
  override them and `/reload` applies the change.

#### Source-charge circuits and potential crafting
- **High-potential chunks**: seeded, spawned by `highPotentialChunkRarity` (default one per 64 chunks);
  operators can query them with `/rotten_ruins high_potential`.
- **Simple field meter**: refreshes twice a second and reports high / medium / low potential for the current
  chunk plus a 5-chunk circular neighbourhood.
- **Grounding stake / source wire**: the stake places on all six faces and needs solid support; the wire
  loses one step of potential per 4 blocks along the shortest path (15 → 1) and lights up only when the
  same network reaches both a high- and a low-potential stake. Potential is independent of redstone.
- **Source charge filter**: three tabs (manual tuning / lazy mode / band table); parameters `fc`, `B`, `G`
  (0–10, step 0.1) and phase `φ`. Every school has a valid window; Void requires inverted phase.
  A client-side oscilloscope renders the waveform live.
- **Charge collector**: empty bottles in the left slot, filled bottles out of the right, using the element
  of the nearest upstream filter. Supports hopper automation, a 3000-work-unit progress bar and saving.
- **Arcane Instrument Altar**: 15 ring slots plus one central output slot. It only runs when wired into a
  potential circuit, and higher potential is faster (at 15 the craft halves its 10 s base time).
  Recipes accept item / tag / NBT-subset ingredients and are consumed once crafting starts.
- **Material chain**: source charge sand → source wire / source mana plate → seven elemental mana plates
  (one source plate plus 8 same-element bottles, returning 8 empty bottles).

#### Corruption faction
- **Host family, 11 mobs**: Assimilated Bat, Chicken, Cow, Pig, Sheep, Wolf, Villager, Zombie, Skeleton,
  Player and Skeletal Remains.
- **Degenerate family, 4 mobs**: Degenerate Cow, Pig, Villager, Zombie.
  **Refactored family, 4 mobs**: Attacker, Gestaltomb, Longibrachiate, Shocker.
  **Linked**: Humus Column, Summoning Pillar. **Original**: Primordial Worm.
  **Deteriorated**: Crawlfex.
- **Family skills**: 7 shared skill entities (Acid Ball, Bone Spike, Corruption Dart, Corruption Pool,
  Poison Arrow, Flesh Wall, Flesh Shield), plus per-family signature skills; the 11 Host mobs share one
  skill set with per-mob differences.
- **New Hall-side mobs**: Rampart, Hearthstone, Winding Column, Death Hunter, Abnormal Squid.
- **Infection evolution**: an infected mob killed 3 times upgrades to a corruption variant; summoning
  pillars and humus columns infect blocks and spread the biome, and corruption mobs spawn inside it.
- **Cross-faction targeting**: Hall and corruption can target each other and rewrite factions through the
  conversion rules; corruption now uses the same `InfectedTargetGoal` as the Hall.
- **Loot**: Host / Degenerate / Refactored have independent material pools that can drop together, and
  Longibrachiate additionally drops its head and tooth. Looting follows vanilla scaling.

#### Carrion Marrow (dimension)
- Height raised from 256 to **768** (sea level stays at 96) and biomes expanded to **5**: Carrion Marrow,
  Hall Ocean, Shattered Lands, Folded Fields and Pierced Continent.
- **Shattered Lands**: a 192-block horizontal grid, three layers of static rotated cubes 80–148 blocks
  across, reading as layered islands above a low basin.
- **Folded Fields**: thick strata folded along a diagonal axis in roughly 800-block groups
  (flat → steep rise → fold back → high platform → return), with air gaps and overhangs at the folds.
- **Pierced Continent**: a thick continent around Y=340 cut by two spherical voids of radius 82–116,
  producing shafts, halls, rock bridges and thin walls.
- Biomes and terrain share the seeded `surreal_regions` noise and blend continuously across region borders.
  Only newly generated chunks are affected; existing chunks are never rewritten.
- **High-altitude ore**: domite and domerite distribute triangularly across Y=128–672 (4 / 8 attempts per
  chunk) and only replace Hall Stone.
- **Island vegetation**: the new biomes grow Hall Trees and Hall Grass on the highest grass surface and on
  standable grass at mid and lower levels.
- **Spreading biomes**: `hall_wasteland` and `corruption_wasteland` are real biomes written as blocks
  spread, and Hall / corruption mobs spawn inside them. The biome follows the blocks, so no hand-drawn
  border is needed.

#### Anomaly attacks and tracking projectiles
- **Heat / Cold / Acid anomalies**: 10 stacks, and a full stack bursts, resets and grants the matching
  300-tick adaptation. Adaptation only blocks further accumulation of its own type.
- **Decay**: after 60 ticks without a successful hit the stack drops by 1, then by 1 every 20 ticks;
  each type keeps its own timer.
- **Per-move accumulation**: every mob and move declares its anomaly type, amount per hit and minimum
  interval (most channeled skills tick once per 20). Damage is accumulated at the end of the player tick
  using the final cancelled flag and the final positive damage, so shields, absorption and later
  cancellations do not count.
- **Ghost projectiles**: every 6th Infected Skeleton shot is a ghost — translucent texture, phases through
  blocks and mobs while homing, sweeps its collision box per tick, lives 100 ticks and scatters END_ROD
  particles when it expires.

#### Weapons and gear
- **Skyfall Verdict**: one key, three states (tap to dash, hold to raise a beam, sneak-hold to open the
  field) sharing one cooldown pool. Damage is fixed; coverage scales with how high you aim.
- **Silent Daylight**: any left-click hit adds three long blue-white slashes (5% max health rewrite plus
  frost damage), while raising the blade opens a frost field (healing, freezing, sweeping projectiles away).
  The blade uses its own water-caustics fragment path.
- **Crimson Vow**: right-click raises the blade to block (frontal only, resolved through a mitigation bar);
  sneak-charging unfurls the crimson sigil. Holding it draws a three-layer procedural halo behind you
  (Voronoi blocks, hexagram, moving ribbon).
- **Hall Boomerang**: throw it and it homes back through terrain after a hit or a timeout; damage,
  durability cost and cooldown are declarable, and it scatters spinning dust.
- **Other weapons and foci**: Death Dagger, Death Star, Spear of Judgment, Void Sword, True Copper
  Shortsword, Olympiad Cube, plus the crude and domerite tool lines.
- **Materials**: source charge sand, source mana plate, four chips (blank / scanning / extraction /
  enhanced), chitin polymer, hardened fragments, void shard / rune / essence, assimilated flesh / tooth /
  carapace, degenerate heart, acid sac, Longibrachiate head and tooth, infected bone, living flesh and the
  magic catalyst.

#### UI, text and shaders
- **Section-code text effects**: `§[rrs:<effect>:<params>]…§[/]` with colour effects (gradient / solid /
  rainbow / pulse) and motion effects (shake / wave / glitch); `grad` accepts `speed=` for flowing colours.
  Three client mixins handle marker parsing and measurement, the per-frame overlay for cached text, and
  draw-coordinate offsets — rendering only, so content, width and wrapping are untouched.
- **`/rrsfx` demo**: preview screen, chat test, runtime on/off, stats, reset and help; `chat` accepts `&`
  as a typed substitute for `§`.
- **Creative tab sections**: divider rows (flowing ribbon plus centred title) split one creative tab into
  spawn eggs / materials / magic / weapons & tools / blocks / miscellaneous. Items are de-duplicated
  across sections, the last section sweeps the namespace, and searching falls back to the vanilla flat list.
- **Other UI**: tooltip shader (thermal), difficulty select screen with procedural sigils, custom-effect
  HUD, boss health bars, wand HUD and the wheel / loadout screens.
- **Cosmic and mask layers**: the `hall:cosmic` model loader plus multi-layer `mask_layers` item shaders;
  Crimson Vow and Silent Daylight each have their own fragment path, with shader-pack (deferred replay)
  compatibility.
- **Slashes**: `SlashEngine` scheduling, multi-style `SwordAuraEntity`, thinnest-axis plane detection from
  baked model vertices, an anchor editor and offline preview tools.
- **More shaders**: black-hole distortion, shockwave, verdict beam / field, sigils, spell constellation and
  starfield, SDF rounded UI, creative dividers.
- **Story text**: typing, exit animation, fonts (default / Wenkai / Xiaowei / Mashan), paging and
  declarable conflict policy.

#### Story and rituals
- **JSON story system**: `data/<ns>/stories/`, `story_rules/` and `story_text`, supporting condition
  matching, weighted selection, no immediate repeats, delayed scheduling, one-shot / cooldown flags,
  paging and spatial text. Loaded on server start and `/reload`; a broken file keeps the previous snapshot.
- **Built-in stories**: opening, respawn (normal / drowning / fall / mob kill / rapid deaths) and the
  fleshbed ritual.
- **Fleshbed**: right-click to feed rotten flesh, insert magic catalysts, then start the Hall summoning
  ritual after checking for standable space around it.

#### Advancements
- Advancement tree: the "Rotten Ruins" root (first join), "Death" (obtain the Death Star) and the
  "Magic, a Piece of Cake!" branch with 14 production-line advancements (survey / production / plates /
  collection).

#### Engineering and compatibility
- **Layered architecture**: `core` (pure JDK, compiled on an empty classpath) / `api` / `gameplay` /
  `content` / `platform` / `client`, with core, magic-API, spell-container and whole-source dependency
  gates wired into the build.
- **MeXin**: a method-level coremod injection framework (`MIX_BEFORE_ENTER` / `MIX_BEFORE_RETURN` /
  `BODY` / `EXCHANGE`), shipped as a single jar that is both launch plugin and mod.
- **Compatibility**: Jade and JEI integration, shader-pack patches and deferred replay.
- **Tooling**: `tools/shader-preview`, `tools/gui-preview` (offline shader compilation and GUI composition
  preview), `tools/tests` (pure-JDK rule tests) and `tools/deploy-test.ps1`.
- **Datagen**: lang, block states / models, recipes, loot tables, advancements, worldgen and rule JSON are
  all generated.
- **Debug commands**: `/anomaly`, `/collapse`, `/spread`, `/threat`, `/difficulty`, `/infcore`, `/rrsfx`,
  `/meteorevent`, `/blackhole`, `/story`, `/ce`, `/crimsonvow` and `/rotten_ruins high_potential`.

### Changed
- **Global rename**: mod name Rotten Ruins, with id, packages, assets and config directories unified as
  `rotten_ruins`; no legacy aliases are kept.
- **Dimension rework**: Carrion Marrow keeps its dimension id, but terrain, biomes, ore and vegetation were
  rebuilt.
- **Skyfall Verdict feel pass**: the short-press window moves from 0.3 s to 0.4 s (8 ticks) and the field
  threshold to 0.7 s (14 ticks), with an audible cue at each boundary. The beam now deals two waves
  (24+5, then 12+4 ≈ 45) timed to the descending scan sphere and lives 12 s → 8 s. Each field pulse drops
  aerial blades that resolve on the impact frame and locks up to 3 targets. The dash speed follows a
  "34% faster start, 58% slower finish" envelope and keeps 38% of its speed at the end; a whiff records
  only 40% of the cooldown and does not advance the chain level; damage taken while dashing is reduced to 38%.
- **Corruption targeting**: from a 5-second scan over 64 blocks to a per-tick scan over 40 blocks, matching
  the Hall.
- **Corrupted blocks**: now spread on their own random ticks like Hall blocks; Corrupted Short Grass is
  judged the instant a block is infected and is now two blocks tall with lower density.
- **Creative tab item list**: declared as sections instead of a flat list; the item set is unchanged.
- **Spell slots**: default 10 → 5 (cap 64); when capacity shrinks, overflow items go to the inventory first
  and the remainder drops.
- **Text effects**: `grad`'s `speed` now means cycles per second (`speed=0.5` ≈ a 2 s loop); the `/anomaly`
  header, `/collapse types` header, the "Rotten Ruins" advancement title and the creative tab label now use
  flowing gradients; the "Incomprehensible" tier uses the glitch effect instead of `§k` fake obfuscation;
  `═══` command headers use a gold gradient.
- **Threat points**: now drive targeting — below the threshold Hall mobs will not aggro, and player death
  resets them.
- **Wands and the wheel** share one casting timeline and cast-start validation (a wand on cooldown returns
  immediately without entering the use state).
- **Code style**: comment ratio across the project 26.4% → 12.6%, and `new ResourceLocation` is routed
  through `ModUtils.modLoc`.

### Removed
- **Nightly meteor shower**: `MeteorShowerEvent` is gone, so meteors no longer spawn at night automatically.
  `MeteoriteEntity` (landing AoE, block spread, shockwave, rendering) and `/meteorevent` are unchanged, and
  `MeteorConfig.json5` now holds only landing-behaviour values.
- **Legacy magic container API**: `PlayerMagicPool`, `ByteItemHandle` and the `changeSlot` / `getSlotnow` /
  `upDate` aliases are deleted.
- **Standalone infcore jar**: the infection core is bundled; remove `infcore-*.jar` from `mods` when upgrading.
- **Garden monuments disabled**: the Unmanned Throne and Sea Crown structure code is kept but never
  generated.

### Fixed
- **Players kicked back to the login screen**: `AttachCapabilitiesEvent` fires inside the entity constructor,
  before the attribute map exists, so reading it threw a `NullPointerException` and aborted capability
  attachment. `RegisterAttributes.valueOf` now returns the fallback while the attribute map is `null`.
- **Both factions' spawn eggs could not spawn** (NPE from a `null` `faction` during construction).
- Corrupted blocks not spreading, random-tick grass growth, and the wrong timing for infected grass.
- The verdict field emitted a blade before checking whether the caster was still present; kill attribution
  for field blades and beam damage (drops and advancements).
- Negative amplitude when the sigil shader turned off.
- `mask_glow` sampling and layer handling.
- Spell slots and mana being reset on respawn; same-item spell cooldowns shared by all players (they used to
  live on the `Item` singleton).
- Model loading crash; Hall Grass / Flower / Ash Cactus block behaviour.
- walk / run animation trigger markers aligned with the actual clips.
- 68 compile failures caused by the fully opened `accesstransformer.cfg`, plus the missing registrations of
  `AccessorRenderStateShard` and `CrimsonVowBlockMixin`.
- Ghost projectile texture, phasing and dissipation particles.

### Known Issues
- Effect spans do not continue across `Component` children or `Font.split` line wraps; text containing RTL
  characters keeps only static colours; `drawInBatch8xOutline` (glowing-sign outline) lets vanilla override
  the halo colour; the `glitch` colour-channel shift is invisible on pure-white text; short aliases
  (`§g` etc.) are not enabled.
- Terrain changes only affect newly generated chunks; old-world height changes, the old/new generation
  boundary and the vegetation behaviour have not been verified in game — a fresh world is recommended.
- Garden monuments (Unmanned Throne / Sea Crown) are disabled, so `/locate structure` will not find them.

### Installation
- Minecraft 1.20.1 + Forge 47.4.20+; Cloth Config, GeckoLib and Curios 5.4+ are mandatory.
- Main network protocol is **4**; client and server must run the same version.
- Upgrading: delete `infcore-*.jar` from `mods`. Development saves are not required to be compatible
  (the dimension directory and data namespace moved).
