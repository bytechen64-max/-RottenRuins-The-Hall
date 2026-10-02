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

## [Unreleased] — Skyfall Verdict: VFX & game-feel rework
> Scoped to "make the skill effects cooler and the controls feel better". The three existing
> design rules are unchanged: damage is fixed, coverage scales with height, and the three
> skills share one cooldown pool.

### Added
- **Unified hit-feedback bus (`VerdictFeedback`)** shared by all three skills:
  hit-stop (red flash + one squashed animation frame), hit pitch rising with the number of
  targets struck, particle bursts, landing impact rings, camera shake and FOV kicks.
- **Camera feedback (`VerdictCameraEffects`)** via `ViewportEvent.ComputeCameraAngles` /
  `ComputeFov`, capped at 1.35° of shake and 9° of FOV.
- **Verdict sword-drop entity (`VerdictSwordDropEntity`) and its renderer** — a blade that
  falls from the sky and stays planted in the ground.
- **Dash damage mitigation** — damage taken while dashing is reduced to 38%.

### Changed
- **Three-state timing rework**: the short-press window is widened to **0.4 s (8 ticks)** and
  the field threshold moved to **0.7 s (14 ticks)**, with an audible cue at each boundary.
- **① Skyfall Verdict (beam)**
  - Damage is now **two waves** (24+5, then 12+4 ≈ 45) timed to the descending scan sphere.
  - The damage moved into `VerdictBeamEntity`, so "where the visual sweeps" and "where it
    lands" are the same event.
  - Landing now has an impact ring, dust/block particles and a camera kick.
  - Lifetime 12 s → **8 s**; the collapse is now a bottom-up implosion with a flash instead
    of a uniform fade. Aiming too low is no longer silent.
- **② Rend the Sky (dash)**
  - Trail density is now derived from distance (one blade every 2.2 blocks) and lives longer.
  - Speed follows a "34% faster start, 58% slower finish" envelope whose mean is 1.0, so the
    dash distance is unchanged.
  - 38% of the speed carries over at the end instead of a hard stop.
  - Struck targets get slash particles and a small shockwave.
  - **A whiff records only 40% of the cooldown and does not advance the chain level**, so
    using the dash for mobility is no longer punishing.
- **③ Verdict Field (sword array)**
  - Opening now drops **7 pillar blades** around the rim with staggered landing times.
  - Each pulse now drops **aerial blades** instead of three parallel ones, and damage lands
    on the impact frame; a pulse locks up to 3 targets.
  - The array **stops striking** when the caster leaves (it used to only dim), with the
    shader dimming in step.
- **Shaders**: beam gains a two-wave downward scan band, a bottom-up collapse and a flash;
  the field gains a per-pulse breath and an off-state. Neither samples the screen, so no new
  late-replay pipeline is required.

### Fixed
- The field used to emit a sword *before* checking whether the caster was still present.
- Kill attribution for both the beam and the field blades (field kills now count as the
  player's, so drops and advancements work).

