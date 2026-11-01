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

## [Unreleased] — Section-Code Text Effects (v1)
> A namespaced custom formatting-code layer for text: `§[rrs:<effect>:<params>]…§[/]`.
> Client-side rendering only; text content/width/wrapping are unchanged and the markers
> are zero-width under the vanilla parser. v1 ships colour effects (gradient/solid/rainbow/pulse)
> and motion effects (shake/wave/glitch), a `/rrsfx` demo command and config entries.

### Added
- **Three client mixins**: `StringDecomposer` (marker parsing/measurement),
  `ClientLanguage#getVisualOrder` (per-frame overlay for cached `FormattedCharSequence`),
  `Font$StringRenderOutput#accept` (temporary draw-coordinate offsets only; `renderChar` untouched).
- **Pure-Java engine** (unit-testable without launching the game): `TextEffectParser`,
  `EffectPlan`, `EffectColor` (linear-light gradients, HSV), `EffectMotion`
  (deterministic shake/wave/glitch), `TextEffectPlanCache` (LRU).
- **Builder API** `api/text/TextEffectText`: `gradient/solid/rainbow/pulse/shake/wave/glitch`
  plus `open/close` for generating marked-up text from items, commands and demos.
- **Demo**: client command `/rrsfx` (`screen` preview screen / `chat` local chat test /
  `on|off` runtime switch / `stats` / `reset` / `help`); `chat` accepts `&` as a typed substitute for `§`.
- **In-game test text**: `INF_ENDER_PEAR` display name is now
  `§[rrs:glitch:amp=2.0]…§[/]` to verify the corrupted-text renderer.
- **Config**: `textEffectsEnabled`, `textEffectsFontHooksEnabled` (safety bypass),
  `textEffectIntensity`, `textEffectSpeed`, `textEffectUseGameTime`, `textEffectMaxAnimatedGlyphs`.
- **Design docs**: `docs/proposals/custom-section-code-text-effects.md` and
  `docs/proposals/text-effects-preview/` (animated previews / interactive playground).

### Changed
- **Flowing gradients**: `grad` now accepts `speed=` (`§[rrs:grad:#a:#b:speed=0.4]`); with `speed > 0`
  the colours flow along the text over time (seamless cyclic wrap), while the default `0` keeps the old
  static look used by existing content and the preview GIFs. `TextEffectText.gradient(text, from, to, speed)`
  exposes the same option.
- **Migrated display texts now flow**: Spell Constellation title (`speed=0.5`), the `/anomaly` status header
  and `/collapse types` header (`speed=0.5`), the "Rotten Ruins" advancement title (`speed=0.5`), and the
  creative-tab label / panel title `itemGroup.rotten_ruins.main` ("Rotten Ruins" / "腐朽之疫", `speed=0.6`);
  the creative-tab section titles keep using `rainbow` (speed raised to 2.2).
- **Flow speed semantics**: `grad`'s `speed` now means cycles per second (`speed=0.5` ≈ a 2 s loop), 4× faster
  than the first revision so the motion is clearly visible.
- **Creative-tab section titles**: removed the private per-glyph HSV implementation in
  `CreativeTabDividerRenderer` (the `drawFlowingText` helper and its two constants) and replaced it
  with a single `drawString(Component)` using `§[rrs:rainbow:sat=0.45:speed=1.3]`, sharing one
  implementation/config with all other rainbow text; a leading `§6` keeps the title gold when effects are off.
- **"Incomprehensible" difficulty tier**: `HallDifficultyEventHandler.resolveDisplayName` now uses
  `§4§[rrs:glitch:amp=2.0]…§[/]` instead of the `§c§k!!` fake-obfuscation (the `§4` keeps the fallback red).
- **Command feedback headers**: the `/anomaly` status header and `/collapse types` `═══ … ═══` line
  now use a gold gradient (with `§6` as the degraded fallback).
- **Spell Constellation title**: `MagicScreen#drawHeader` now uses a bright-gold → dark-gold gradient
  (`§6` fallback).
- **datagen copy**: the "Incomprehensible" difficulty name/tooltips (hard = shake, incomprehensible = glitch),
  the "Rotten Ruins" advancement title (gradient) and the "Obtain the Death Star" description (glitch)
  now carry section-code markers.
  - **Note**: lore/flavour lines that go through `FlowingNameColors` / `TooltipLines` were deliberately
    left untouched — their per-character rebuild would split the markers across sibling components and
    render them literally.

### Known Issues
- An effect span does not continue across `Component` children or `Font.split` line wraps.
- Text containing RTL characters skips the per-frame overlay (bidi reordering breaks glyph ordinals).
- For `drawInBatch8xOutline` (glowing-sign outline) the halo colour is overridden by vanilla;
  the core pass still shows the gradient.
- The `glitch` colour-channel shift is invisible on pure-white text (positional offsets still apply).
- Short single-letter aliases (`§g` etc.) are not enabled yet; only the namespaced syntax is supported.

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

## [Unreleased] — Fix: crash on login caused by an uninitialized attribute map

### Fixed
- **Players could not enter the world (kicked back to the login screen).**
  `AttachCapabilitiesEvent` is fired from inside `Entity`'s constructor, before
  `LivingEntity` assigns its attribute map. Our own handler runs in that same event and does
  `new TwistedPoint(player)` → `MagicStats.maxMana` → `RegisterAttributes.valueOf`, which read
  the attribute map and threw a `NullPointerException`. Escaping the listener aborted the whole
  `AttachCapabilitiesEvent` (no magic pool, no mana pool), which surfaced in the log as
  `Couldn't place player in world`.
  `RegisterAttributes.valueOf` now returns the fallback while the attribute map is `null` —
  at that moment no attribute modifier can exist yet, so the fallback *is* the real value.

## [Unreleased] — Creative inventory: section divider rows

### Added
- **Sectioned creative tab (`SectionedCreativeModeTab`).** One tab whose items are cut into
  sections, each introduced by a divider row: a flowing colour ribbon plus a centred,
  colour-cycling title. Sections are padded to whole rows automatically.
  - Declared from the registry side with
    `SectionedCreativeModeTab.builder().section(title, generator)...`.
    The inherited `displayItems()` is disabled — it would be overwritten in `build()`,
    so it throws instead of silently doing nothing.
  - Items are de-duplicated across sections (first section wins), which is why the trailing
    "Miscellaneous" section can simply sweep the whole namespace: newly added items can never
    be lost by forgetting to register them.
- **Divider shader `hall:gui_tab_divider`.** An aurora ribbon in GUI space (horizontally
  flowing multi-colour noise, a bright core filament, soft vertical falloff), submitted
  immediately via `BufferUploader.drawWithShader`. If the shader fails to load, the divider
  falls back to a plain solid line — sectioning itself keeps working.
- **`CreativeModeInventoryScreenMixin`.** Inserts nine empty slots as a divider row into the
  vanilla "flat item list + 5×9 viewport" grid, then draws the ribbon and the title on
  `ContainerScreenEvent.Render.Foreground`. While the search box has a query, the vanilla flat
  result list is kept and no sectioning happens.

### Changed
- **`RegisterTab` now declares its items as sections:** Infected & Hall Creatures /
  Hall Materials / Weapons & Tools / Hall Building Blocks / Miscellaneous.
  The item set is unchanged; it just gained a visible structure.
- Five new section titles in `LangDataCN` / `LangDataEN` (`itemGroup.hall.main.section.*`).

### Notes
- Divider rows are **client-side only**: the creative tab's item list is built on the client
  anyway (`CreativeModeTabs.tryRebuildTabContents()` has exactly one caller,
  `CreativeModeInventoryScreen`), so there is no networking, save data or server involvement.
- `getDisplayItems()` still returns the same clean flat list, so JEI and other mods see no
  change; the mixin only applies while a sectioned tab is selected, leaving all other tabs
  untouched.

## [Unreleased] — Removed the nightly meteor shower

### Removed
- **Automatic meteor-shower event**: `MeteorShowerEvent` is gone — meteors no longer spawn
  around players at night. `MeteoriteEntity` (landing AoE, block spread, shockwave and
  rendering) and the `/meteorevent` set-piece are unchanged;
  `config/rotten_ruins/MeteorConfig.json5` now holds only landing-behaviour values (the old
  event-generation options such as `enable` and `intervalBase` were dropped).

