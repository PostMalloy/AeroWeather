# AeroWeather

## Overview

AeroWeather is a NeoForge mod for Minecraft 1.21.1 that adds a simulated
wind system:

1. Wind has a direction and intensity (0–100) that drifts naturally over
   time and gusts occasionally.
2. Simple directional particles show which way the wind is blowing from.
3. Vanilla rain and thunderstorms increase wind severity (thunderstorms
   more than plain rain).
4. `/aeroweather wind <direction> <strength>` lets operators directly set
   wind direction/intensity; `reset` returns to natural simulation,
   `info` reports current state.
5. With **Create Aeronautics** (built on the **Sable** physics library)
   installed, wind physically pushes flying contraptions around — this
   is the payoff feature everything else supports.

See "Out of scope" below for what this explicitly does *not* include yet.

## Tech stack (pinned versions)

`gradle.properties` is the single source of truth — check it, don't trust
this list if they ever diverge.

- Minecraft `1.21.1`, NeoForge `21.1.248`
- ModDevGradle plugin `2.0.144`
- Java 21 toolchain, Gradle `9.2.1`
- Mojang mappings + Parchment `2024.11.17`
- `mod_id=aeroweather`, `mod_group_id=com.postmalloy.aeroweather`
- License: MIT

## Build / run commands

- `./gradlew build` — compile + build the mod jar
- `./gradlew runClient` — launch a dev client with the mod loaded
- `./gradlew runServer` — launch a dev dedicated server
- `./gradlew runData` — run data generators (outputs to `src/generated/resources`)
- `neoForge.ideSyncTask generateModMetadata` runs automatically on IDE
  project sync, so `mods.toml` template substitutions stay current
  without a manual build

## Package structure

Root package `com.postmalloy.aeroweather`. This table is the target
architecture — keep it in sync as code is actually added; don't let it
drift stale.

```
AeroWeather.java                      main @Mod class: registries, config, subsystem bootstrap
AeroWeatherClient.java                 client @Mod entry: config screen registration

config/
  AeroWeatherCommonConfig.java        drift rate, gust chance/magnitude, rain/thunder boost, sync thresholds
  AeroWeatherClientConfig.java        particle toggle, max count, spawn radius, outdoors-only flag

wind/
  WindDirection.java                  cardinal enum + degree/vector helpers
  WindState.java                      direction/strength + drift target + gust + weather-boost + override; NBT I/O
  WindSavedData.java                  SavedData, one per ServerLevel via getDataStorage().computeIfAbsent(...)
  WindSimulator.java                  LevelTickEvent.Post @ 20-tick cadence: drift/gust/weather-boost/override
  WindOverride.java                   operator-set override value type
  WindHeightScaling.java               pure function: base strength -> elevation-scaled strength (power-law, 0 at/below sea level)

network/
  NetworkHandler.java                 RegisterPayloadHandlersEvent registration
  payload/ClientboundWindSyncPayload.java   record CustomPacketPayload: dimension id, directionDeg, strength
  ClientPayloadHandler.java           updates client.ClientWindState on receipt
  WindSync.java                       decides when to broadcast: force (join/dimension-change/respawn/command) vs threshold+heartbeat (per simulation step)
  PlayerSyncListener.java             PlayerLoggedInEvent/PlayerChangedDimensionEvent/PlayerRespawnEvent -> WindSync.sendTo(player)

client/
  ClientWindState.java                client-side cache of latest synced wind per dimension
  particle/
    WindStreakParticle.java           TextureSheetParticle + nested Provider
    AeroWeatherParticleProviders.java RegisterParticleProvidersEvent registration
    WindParticleSpawner.java          ClientTickEvent.Post: spawns particles around player from ClientWindState

registry/
  AeroWeatherParticles.java           DeferredRegister<ParticleType<?>>: WIND_STREAK, WIND_GUST
  AeroWeatherCommandArgumentTypes.java  DeferredRegister<ArgumentTypeInfo<?,?>>: registers DirectionArgument for command-tree sync

command/
  AeroWeatherCommand.java             /aeroweather wind <direction> <strength> | reset | info  (op-level)
  DirectionArgument.java              ArgumentType<Float>: cardinal keyword OR degree float

integration/
  ModCompat.java                      ModList.get().isLoaded(...) checks, modid constants
  aeronautics/
    WindForceApplier.java             interface: tick(ServerLevel, WindState)
    NoopWindForceApplier.java         default no-op
    AeronauticsWindForceApplier.java  ONLY class allowed to reference Create/Aeronautics/Sable types directly
    AeronauticsIntegration.java       resolves Noop vs real applier based on ModCompat
```

Built so far: `AeroWeather.java`, `AeroWeatherClient.java`, the full
`wind/` package (M1), `command/` + `registry/AeroWeatherCommandArgumentTypes.java`
(M3), the full `network/` package + `client/ClientWindState.java` (M2),
`client/particle/` + `registry/AeroWeatherParticles.java` (M4), and the
full `config/` package (M5) — **checkpoint reached: the mod is fully
standalone, config-tunable, zero external dependencies**. Still planned:
`integration/` (M6/M7).

Particle textures live at
`assets/aeroweather/textures/particle/windparticle{1-8}.png`, declared
in `assets/aeroweather/particles/wind_streak.json`. `WindStreakParticle`
cycles them exactly once over its own randomized 24-40 tick lifetime via
vanilla `TextureSheetParticle#setSpriteFromAge` (`SpriteSet.get(age,
maxAge)` spread evenly across `[0, lifetime]`), so animation speed is
tied to how long that particular particle happens to live. An earlier
version used a fixed 100ms-per-frame cadence via a `SpriteSet`
indexed-access hack (`get(frame, FRAME_COUNT - 1)` resolves to exactly
`frame`), but that looped 1.5-2.5x before the particle disappeared
(reading as stuttery) since it wasn't tied to the per-particle lifetime
— replaced with the lifetime-tied approach above.

A second particle type, `WIND_GUST` (textures
`gustparticle{1-8}.png`, declared in `wind_gust.json`), shares the
exact same `WindStreakParticle` class and rendering/orientation logic —
only the registered `SimpleParticleType`/`SpriteSet` differ, wired via a
second `event.registerSpriteSet(...)` call in
`AeroWeatherParticleProviders`. It's spawned by `WindParticleSpawner`
only once the elevation-adjusted strength exceeds
`AeroWeatherClientConfig.GUST_PARTICLE_MIN_STRENGTH` (default 50/100),
using its own accumulator so its rate is independent of the always-on
`WIND_STREAK` spawning. This is a purely client-side threshold on the
already-synced strength value — no separate gust-state sync was needed.

Particles spawn at a random angle around the player (not just upwind —
they drift toward the travel direction regardless of spawn angle, so
this only affects ambience, not readability); a per-particle +/-5 degree
direction jitter keeps the drift from looking perfectly uniform. Both
spawn rate and drift speed scale with wind strength (0.05-0.4
blocks/tick); vertical speed is clamped to exactly 0 for now.
`WindStreakParticle` bypasses `TextureSheetParticle`'s 7-arg constructor
deliberately — it chains to vanilla `Particle`'s randomizing constructor,
which jitters velocity by up to +/-0.4 per axis and unconditionally adds
+0.1 to `yd` rather than passing given velocity through untouched — and
sets `xd`/`yd`/`zd` directly instead.

Orientation is NOT the default camera-billboard (`SingleQuadParticle`'s
`LOOKAT_XYZ`, which always faces the camera regardless of travel
direction — a billboard looks identical from every angle no matter which
way it's actually moving, defeating the point of a directional texture);
`WindStreakParticle` overrides `getFacingCameraMode()` to return a fixed
world-space orientation computed once from the velocity vector at spawn
(a vertical card containing the travel vector, normal perpendicular to
it) and never re-derived from the camera. Two things layer on top of
that fixed orientation:
- **Double-sided rendering**: `render()` is overridden to draw the quad
  twice, once with reversed winding (a 180-degree yaw flip), since
  `PARTICLE_SHEET_TRANSLUCENT` never sets an explicit GL cull state and
  a fixed (non-billboard) card can plausibly end up back-face-out from a
  given angle.
- **Left/right texture mirroring**: since every particle from the same
  wind shares one fixed orientation, a particle to the player's left and
  its mirror counterpart to the right would otherwise show identical
  texture handedness instead of reading as a mirrored pair. "Left" is
  relative to facing upwind (the reference direction the orientation
  scheme is built around), computed once at spawn from a cross-product
  sign, and applied by swapping `getU0()`/`getU1()`.

Verified empirically, not just derived, across several rounds of live
testing (screenshots, F3 debug coordinates, RCON): direction math is
correct at both cardinal and diagonal wind angles; wind traveling
perpendicular to the camera's view axis shows full-width shapes,
parallel shows thin edge-on slivers (only possible with a genuine
world-space orientation, not a billboard); and a reported "particles
only spawn on one side" issue was root-caused to `canSeeSky` correctly
filtering spawns near terrain, not a spawn or orientation bug — raw and
post-filter spawn-angle distributions matched exactly (794/794) once
tested in open sky at altitude.

## Wind system design

- Per-`ServerLevel` state stored in `SavedData` (naturally per-dimension —
  no special-casing needed, e.g. `isRaining()` is just always false in
  the Nether/End so the weather-boost term is always 0 there).
- Effective strength = `overridden ? overrideStrength : min(clamp(baseStrength
  + gustStrength + weatherBoost, 0, 100), currentStrengthCap())`.
- Natural drift: periodically re-roll a target direction/strength within
  a bounded delta, lerp current value toward it each simulation step
  (plain random-walk-with-lerp — no noise library needed for v1).
- Gusts: short probabilistic additive spikes that decay, layered on top
  of the drift value.
- Wind intensity scales with elevation above sea level via
  `wind/WindHeightScaling.java`, approximating the real-world wind
  profile power law: 0 at or below sea level, reaching the unmodified
  base strength at a configurable reference height above sea level
  (`AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL`, default
  100 blocks), and growing further above that up to a configurable cap
  (`HEIGHT_MAX_MULTIPLIER`, default 3x). Kept as a standalone pure
  function (not baked into `WindState`, which stays purely per-dimension
  with no positional input) since a server-side consumer is expected
  once M7 (Aeronautics force application) needs to sample wind at a
  contraption's position too. Wired into both `WindParticleSpawner`
  (client-side, computed once per tick from the player's Y) and
  `AeroWeatherCommand`'s `wind info` (server-side, from the command
  source's position). The curve parameters live in the common config,
  not the client config: the command needs to read them server-side
  (a client-only config wouldn't even be loaded there), and now that a
  command reports this value it's closer to game state than pure
  rendering flourish anyway. Verified numerically against a live
  server+client, both consumers independently: teleporting through a
  range of heights and checking the computed/reported values matched the
  formula exactly at every sampled point (0 below/at sea level, exactly
  the base strength at the reference height, etc.).
- `weatherBoost` eases (doesn't snap) toward 0 / rain-boost /
  thunder-boost based on `level.isRaining()`/`isThundering()` so weather
  starting/stopping never jump-cuts wind.
- Natural (non-overridden) strength is additionally capped per weather
  tier via `currentStrengthCap()`:
  `AeroWeatherCommonConfig.STRENGTH_CAP_CLEAR`/`_RAIN`/`_THUNDER`
  (defaults 50/75/100), applied as a `Math.min` after drift + gust +
  weatherBoost are summed and clamped. This bounds the base 0-100 value
  only — it's separate from and upstream of `WindHeightScaling`'s
  elevation adjustment, which can still push the reported/rendered
  strength above the tier cap at high elevation. Overridden strength
  (via the command) ignores the cap entirely, same as it already ignores
  drift/gust/weatherBoost. Verified live: forcing `gust.chancePerSecond`,
  `minMagnitude`/`maxMagnitude` high enough to guarantee the pre-cap sum
  exceeds every tier's cap, then cycling weather clear -> rain -> thunder
  via RCON and confirming `/aeroweather wind info` reported exactly
  50, 75, then 100.
- Command override pins an absolute direction/strength and **freezes**
  natural drift while active; `reset` resumes drift from wherever it was.
- Simulation runs at ~1 Hz (every 20 ticks, gated inside a
  `LevelTickEvent.Post` handler — NeoForge doesn't offer a built-in
  tick-filtered event), not every tick.
- Server→client sync uses a `CustomPacketPayload` registered via
  `RegisterPayloadHandlersEvent`. Sync immediately on player
  join/dimension-change/respawn and on command overrides; otherwise only
  on a delta threshold (direction Δ > 2°, strength Δ ≥ 1) or a ~5s
  heartbeat — never every tick.

## Command reference

```
/aeroweather wind <direction> <strength>   set wind (direction: cardinal keyword like "north"/"ne", or a degree float)
/aeroweather wind reset                    resume natural simulation
/aeroweather wind info                     report direction/strength (base and elevation-adjusted at the command source's position) and whether overridden/weather-boosted
```

Requires operator permission (level 2). `<strength>` is clamped 0–100.

## Soft-dependency conventions (Create Aeronautics / Sable)

AeroWeather must load and fully function (wind state, particles,
commands, weather coupling) with **zero** crash risk when Create, Create
Aeronautics, and Sable are absent — they're optional dependencies.

- **Isolation rule**: `AeronauticsWindForceApplier` is the *only* class in
  the mod allowed to reference Create/Aeronautics/Sable/Sable-Companion
  types. `AeronauticsIntegration.get()` must check `ModCompat.isLoaded(...)`
  *before* that class is ever classloaded — never let the JVM attempt to
  resolve an external class reference without a `ModList` check first.
- Build dependency pattern (mirrors the JEI example already commented in
  `build.gradle`): `compileOnly` for compile-time API access + `localRuntime`
  (not `runtimeOnly`) for the full runtime jar in dev, so consumers of
  AeroWeather don't inherit a hard dependency on Create/Aeronautics/Sable.
- Sable Companion is different: it's designed to be embedded
  (`implementation` + `jarJar`), not treated as an optional mod install,
  since it ships safe no-op defaults itself.
- `neoforge.mods.toml` should declare Create/Aeronautics/Sable as
  `type="optional"`, `ordering="AFTER"` dependencies once real modIds are
  confirmed (see open questions below) — no entry needed for Sable
  Companion.
- Because Sable's internal physics API carries no third-party stability
  guarantee, wrap the actual force-application call defensively
  (try/catch, degrade to "log once + disable" rather than crash) once
  that code exists.

## Out of scope (for now)

Not building yet, don't add speculative abstractions for these — YAGNI,
but don't actively make future extension harder either:

- New weather pattern types (tornadoes, custom storms, etc.)
- New blocks/items (wind vanes, anemometers, etc.)
- Anything beyond wind simulation + its Create Aeronautics interaction

## Roadmap / milestones

- ~~M0 — Repo/template scaffolding~~ (CLAUDE.md, LICENSE, MIT license
  wiring, stock example content stripped, git initialized)
- ~~M1 — Wind state core~~ (server-only: `WindState`, `WindSavedData`,
  `WindDirection`, `WindSimulator`)
- ~~M3 — Command~~ (`AeroWeatherCommand`, `DirectionArgument`; sequenced
  alongside M1 since `wind info` was the only way to observe wind state
  before networking/particles existed — verified end-to-end via RCON
  against a live dev server)
- ~~M2 — Networking sync~~ (`ClientboundWindSyncPayload`, `WindSync`,
  `PlayerSyncListener`, `ClientWindState`; verified over a real socket
  connection between a separate dedicated server and client, not just
  integrated singleplayer)
- ~~M4 — Particles~~ (`WindStreakParticle`, `AeroWeatherParticleProviders`,
  `WindParticleSpawner`, `AeroWeatherParticles`; verified visually via
  screenshots of a live client — particles spawn at expected positions,
  render cleanly, and the flipbook demonstrably cycles frames)
- ~~M5 — Config finalization~~ (`AeroWeatherCommonConfig`/`AeroWeatherClientConfig`,
  replacing the placeholder `Config.java`; verified both
  `aeroweather-common.toml`/`aeroweather-client.toml` generate correctly
  and the simulation still runs right reading from config, over a live
  server+client with RCON) — **checkpoint reached**: mod is fully
  standalone, config-tunable, zero external dependencies, all
  non-Aeronautics requirements delivered
- M6 — Create Aeronautics research spike: add Create/Aeronautics/Sable as
  `compileOnly`+`localRuntime`, inspect the actual jars (decompiler /
  `javap`) to confirm sub-level enumeration, exterior geometry access,
  and the real force/impulse entrypoint
- M7 — Aeronautics integration implementation
- M8 — Integration testing & tuning against real in-game airship behavior

## External references

- Create Aeronautics ("Simulated Project"):
  https://github.com/Creators-of-Aeronautics/Simulated-Project — Create
  addon requiring Create + Sable. Modules: `simulated` (core
  assembly/redstone/physics-interaction API), `aeronautics` (propellers,
  hot air, levitation), `offroad` (land vehicles).
- Sable: https://github.com/ryanhcode/sable — the Rapier-based physics
  engine underneath Create Aeronautics. Package root
  `dev.ryanhcode.sable`. Author describes it as intrusive/mixin-heavy;
  its raw API (`dev.ryanhcode.sable.api.physics.PhysicsPipeline`) is
  implementation detail, not a stable third-party surface. Contraption
  physics is impulse-driven from touch via
  `dev.ryanhcode.sable.api.event.SubLevelCollisionEvent`.
- Sable Companion: https://github.com/ryanhcode/sable-companion — the
  sanctioned lightweight library for third-party mods (meant to be
  embedded, safe no-op defaults). Maven: `https://maven.ryanhcode.dev/releases`.
  Confirmed API: `SableCompanion.INSTANCE.getContaining(level, pos)`,
  `.isInPlotGrid(...)`, `.projectOutOfSubLevel(...)`,
  `.distanceSquaredWithSubLevels(...)` — position/sub-level detection
  only, not force application.
- Prior art: "PMWeather Aeronautics compat" — integrates wind by caching
  a sub-level's exterior-surface-patch profile, sampling wind across
  exposed faces with a quadratic pressure model (force ∝ exposed area ×
  wind speed²), summing to net force + torque, and handing that to
  Sable's physics. This is the intended integration pattern to follow in
  M6/M7.
- Modrinth Maven: `https://api.modrinth.com/maven` (use
  `exclusiveContent`/`includeGroup "maven.modrinth"`; coordinates use
  Modrinth *version ids*, not semver strings) — fallback for
  Create/Create Aeronautics/Sable if no dedicated maven is found.
- NeoForge docs: https://docs.neoforged.net/

## Open questions / research spike tracker

Resolve these before relying on them — don't let assumptions calcify:

- Exact in-game modIds for Create, Create Aeronautics, and Sable
  (Modrinth project slugs are known to differ from the real `modId`).
- Exact Modrinth version ids (or dedicated maven coordinates, if one
  exists) for Create/Aeronautics/Sable builds compatible with MC 1.21.1.
- The real Create Aeronautics/Sable API surface for enumerating
  sub-levels/contraptions near a point, accessing exterior geometry, and
  applying force — specifically whether to use
  `SubLevelCollisionEvent`-style synthetic impulses or call
  `PhysicsPipeline.applyImpulse()` directly (needs jar inspection, M6).
- ModDevGradle `2.0.144`'s exact `jarJar` DSL for embedding Sable
  Companion.

Resolved and confirmed working via live testing (kept here as a record,
not because they're still open): `SavedData.Factory`'s deserializer
takes `(CompoundTag, HolderLookup.Provider)`, obtained via
`level.getDataStorage().computeIfAbsent(factory, name)`;
`PacketDistributor.sendToPlayer(player, payload)` and
`.sendToPlayersInDimension(level, payload)` for sync; and the
height-based wind scaling curve (exponent 0.3, 100-block reference
height, 3x max multiplier — see "Wind system design" above and
`AeroWeatherClientConfig`).
