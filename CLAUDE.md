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
  WindDirection.java                  cardinal enum + degree/vector helpers; travelVector(bearingDeg) -> Vec3
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
  ModCompat.java                      ModList.get().isLoaded(modId) + SABLE_MODID constant; zero Sable imports
  aeronautics/
    AeroWeatherBlockTags.java         TagKey<Block> ENVELOPE/LEVITITE/WINDMILL_SAILS; pure data tags, always safe to classload
    WindForceApplier.java             interface: start() (NOT a per-tick method - the real applier drives
                                       itself off Sable's own physics-tick event once started)
    NoopWindForceApplier.java         start() no-op
    AeronauticsWindForceApplier.java  ONLY class allowed to reference Create/Aeronautics/Sable types directly
    AeronauticsIntegration.java       static get(); resolve() checks ModCompat.isLoaded("sable") BEFORE
                                       constructing AeronauticsWindForceApplier, catch(Throwable) -> Noop
```

Built so far: `AeroWeather.java`, `AeroWeatherClient.java`, the full
`wind/` package (M1), `command/` + `registry/AeroWeatherCommandArgumentTypes.java`
(M3), the full `network/` package + `client/ClientWindState.java` (M2),
`client/particle/` + `registry/AeroWeatherParticles.java` (M4), the
full `config/` package (M5), and the full `integration/` package (M6
research spike + M7 implementation) — wind now physically pushes every
Sable-based contraption (Create Aeronautics, Create Offroad, or any
other mod built on Sable), gated on Sable alone being loaded, with zero
hard dependency on it.

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

## M7: Aeronautics wind force

`AeronauticsWindForceApplier` (`integration/aeronautics/`) applies real
force to every Sable sub-level's center of mass, every physics tick.
Gated purely on `sable` being loaded (see "Open questions" below for
why) — a no-op with zero Sable classloading when it isn't.

Force formula and where each input comes from:

| Input | Source | Cached or per-tick |
|---|---|---|
| Sectional area | Closed-form box-silhouette formula (`4 * (hx*hy*abs(dz) + hy*hz*abs(dx) + hx*hz*abs(dy))`) against cached local half-extents + a fresh per-tick local wind direction | Half-extents cached once at assembly; direction/area recomputed every physics tick (cheap vector math only, no block iteration) |
| Lift ratio | `(lift-tagged blocks) / (total non-air blocks)`, one-time scan over the sub-level's local plot bounds | Cached once, **never refreshed** on later block edits (deliberately mirrors Sable's own `buildMassTracker()`) — but computed **lazily on first force application, not eagerly in `onSubLevelAdded`**: live testing found `onSubLevelAdded` fires before a contraption's blocks are actually copied into the sub-level's storage (a real assembled balloon showed a bare 2x2x2 box, `liftRatio=0.0`), so an eager scan there permanently caches an empty snapshot. By the time a sub-level reaches `applyWindForce` it's genuinely running physics ticks, so its blocks are populated. `onSubLevelAdded` isn't overridden at all any more (default no-op); only `onSubLevelRemoved` is, to evict the cache entry |
| Wind strength | `WindSavedData.get(level).wind().strength()` → `WindHeightScaling.scale(...)` sampled at the contraption's own center-of-mass world Y | Wind state updates ~1Hz via `WindSimulator`; height-scaled value and force are recomputed fresh every physics tick |
| Force direction | `WindDirection.travelVector(bearingDeg)`, rotated into the sub-level's local frame via `Pose3dc.transformNormalInverse(...)` | Recomputed every physics tick |
| Application point | The sub-level's local-space center of mass, used as-is (not transformed to world space — see "External references") | Read fresh every tick |

`magnitude = pressureCoefficient * sectionalArea * strength² * liftRatio
* oscillation`, clamped to `AeroWeatherCommonConfig.AERONAUTICS_MAX_FORCE`
— a single scalar multiplier, not a separate vertical lift force, per
the explicit design requirement. 0 lift-tagged blocks → 0 force,
handled as a cheap early-exit. `oscillation = 1 +
oscillationAmplitude * sin(2π * (gameTime/20s) / oscillationPeriodSeconds)`
— a subtle sinusoidal ripple (default ±5% every 2s) phased off the
level's game time rather than accumulated physics-substep dt, so it
stays stable regardless of how many substeps run per game tick.
Live-testing feedback (a real assembled hot air balloon) found the
initial `pressureCoefficient` default (0.01) far too strong; lowered by
50x to `0.0002`.

Lift blocks are the union of three confirmed, pre-existing block tags —
`#aeronautics:envelope` (balloon fabric), `#aeronautics:levitite`
(magic floating rock), `#create:windmill_sails` (Create's sail blocks,
which itself includes `#minecraft:wool`) — verified by extracting
`data/.../tags/block/*.json` from the real jars. All three are pure
data, referenced via `AeroWeatherBlockTags`' `TagKey<Block>` constants
with zero compile dependency on Aeronautics/Create Java classes,
preserving M6's Sable-only compile footprint.

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
  **Wired in `build.gradle` as of M6** — but only Sable actually needs
  `compileOnly` (it's the real API surface used); Create and Create
  Aeronautics are `localRuntime` only, present for dev-client testing but
  never referenced from compiled code, since Sable's sub-level API is
  content-agnostic (see "External references" for why AeroWeather
  doesn't need Aeronautics as a compile dependency at all).
- Sable Companion is different: it's designed to be embedded
  (`implementation` + `jarJar`), not treated as an optional mod install,
  since it ships safe no-op defaults itself. **Wired as `implementation`
  as of M6** (its `sable-companion-common-1.21.1` module, pinned to the
  exact version Sable itself embeds — see "External references"); the
  `jarJar` half (embedding it into AeroWeather's own published jar) is
  still open, see below.
- `neoforge.mods.toml` declares Create and Sable (not Aeronautics —
  AeroWeather doesn't link against it, see "External references") as
  `type="optional"`, `ordering="AFTER"` dependencies using their
  confirmed real modIds (`create`, `sable`). **Wired as of M7** — no
  entry for Sable Companion. **Gotcha found via live testing**:
  `versionRange=""` (intending "no minimum") is NOT parsed by NeoForge
  as unconstrained — it's an impossible range that rejects every
  installed version, crashing load with "Mod aeroweather only supports
  create/sable" even with both mods present and correctly loaded.
  `versionRange="[0,)"` is the correct way to say "any version".
- Because Sable's internal physics API carries no third-party stability
  guarantee, wrap the actual force-application call defensively
  (try/catch, degrade to "log once + disable" rather than crash).
  **Implemented as of M7**: `AeronauticsWindForceApplier` uses two
  tiers — a systemic failure (e.g. during the one-time lift-profile
  build) sets a global `disabled` flag and stops all further work; a
  per-sub-level failure during force application just skips that one
  sub-level for the tick (a single malformed contraption shouldn't take
  the whole feature down).

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
- ~~M6 — Create Aeronautics research spike~~ (added Sable as
  `compileOnly`+`localRuntime` and Sable Companion's common module as
  `implementation`, all from real resolved maven coordinates;
  decompiled/`javap`'d the actual Create/Create Aeronautics/Sable jars —
  confirmed real modIds, the sub-level enumeration/geometry/mass API,
  and the real force entrypoint, superseding everything this file
  previously guessed. See "External references" for the full findings
  and "Open questions" for the one design decision flagged for M7)
- ~~M7 — Aeronautics integration implementation~~ (`integration/` package
  built: `AeronauticsWindForceApplier` applies wind force to every Sable
  sub-level's center of mass, gated on `sable` alone being loaded. See
  "M7: Aeronautics wind force" below for the full design. Verified by
  compiling only — the isolation rule was checked by grepping for
  Sable/Create/Aeronautics imports outside `AeronauticsWindForceApplier.java`
  (found none) — live in-game verification (does a real assembled
  airship actually get pushed, does the force feel right) is explicitly
  deferred to M8)
- M8 — Integration testing & tuning against real in-game airship behavior

## External references

- Create: real modId `create`. Modrinth project `create` (id
  `LNytGWDc`), e.g. version id `UjX6dr61` for 6.0.10+mc1.21.1. Not a
  compile dependency for AeroWeather — `localRuntime` only, present for
  dev-client testing (see Sable below for why).
- Create Aeronautics ("Simulated Project"):
  https://github.com/Creators-of-Aeronautics/Simulated-Project.
  Distributed as a single "bundled" jar (Modrinth project
  `create-aeronautics`, id `oWaK0Q19`, e.g. version id `Vzp221Un` for
  1.3.1+mc1.21.1) that uses NeoForge's `lowcodefml` jar-in-jar container
  to wrap three independently-modId'd mods — confirmed via each nested
  jar's own `neoforge.mods.toml`, extracted from `META-INF/jarjar/*.jar`
  inside the bundled jar:
  - `simulated` (Create Simulated, pkg `dev.simulated_team.simulated`) —
    core assembly/redstone blocks; its `api` package is block/sound
    helpers, not the physics API (that's Sable's, below).
  - `aeronautics` (Create Aeronautics proper, pkg `dev.eriksonn.aeronautics`)
    — propellers, hot air balloons, levitite.
  - `offroad` (Create Offroad, pkg `dev.ryanhcode.offroad`) — land
    vehicles.
  None of these three expose the physics API AeroWeather needs — that's
  entirely Sable's. AeroWeather does **not** need Create or Create
  Aeronautics as a compile dependency at all (confirmed by compiling
  sub-level enumeration/force code against Sable + Sable Companion
  alone, nothing else) — only `localRuntime`, for dev-client testing.
- Sable: https://github.com/ryanhcode/sable — the Rapier-based physics
  engine underneath. Real modId `sable`. Modrinth project `sable` (id
  `T9PomCSv`), e.g. version id `U678xqle` for 2.0.5+mc1.21.1. Package
  root `dev.ryanhcode.sable`. This is AeroWeather's actual compile
  dependency (`compileOnly`+`localRuntime`). Confirmed API surface (all
  under `dev.ryanhcode.sable.api`, decompiled/`javap`'d directly from
  the real jar, not guessed):
  - **Enumeration**: `SubLevelContainer.getContainer(ServerLevel) ->
    ServerSubLevelContainer`, `.getAllSubLevels() ->
    List<ServerSubLevel>`, or `.queryIntersecting(BoundingBox3dc)` for a
    spatial query near a point/region.
  - **Geometry/pose**: `SubLevel.boundingBox() -> BoundingBox3dc` (a
    global-space AABB — no finer per-face/exterior-surface API exists
    anywhere in Sable's, Simulated's, or Aeronautics's jars, so the AABB
    is the practical basis for any exposed-area wind-pressure model, not
    literal per-voxel face sampling), `.logicalPose()`/`.lastPose() ->
    Pose3d` (position + orientation).
  - **Mass**: `ServerSubLevel.getMassTracker() -> MassData` (`getMass()`,
    `getCenterOfMass()`, inertia tensor) — needed to apply force at a
    sensible point and get physically plausible acceleration.
  - **Force application** (the real entrypoint — supersedes this file's
    old guess about `SubLevelCollisionEvent`, which does not exist
    anywhere in the real API): `ServerSubLevel.getOrCreateQueuedForceGroup(ForceGroup)
    -> QueuedForceGroup`, then `.applyAndRecordPointForce(Vector3dc point,
    Vector3dc force)`. `ForceGroup` is a plain record (`Component name,
    Component description, int color, boolean defaultDisplayed`) —
    AeroWeather can construct its own (e.g. a `WIND` group) directly
    with `new ForceGroup(...)` without touching the built-in
    `ForceGroups` registry (GRAVITY/DRAG/LEVITATION/BALLOON_LIFT/
    PROPULSION/LIFT/MAGNETIC_FORCE), which avoids a compile dependency
    on Veil — confirmed via a compile-time smoke test: referencing
    `ForceGroups.DRAG.get()` fails to compile without Veil on the
    classpath (its registry entries are typed as Veil's
    `RegistrationProvider`/`RegistryObject`), while constructing a raw
    `ForceGroup` does not. Lower-level alternatives also exist —
    `RigidBodyHandle.of(ServerSubLevel).applyImpulseAtPoint(Vec3, Vec3)`
    and raw `PhysicsPipeline.applyImpulse(...)` — but `QueuedForceGroup`
    is the one built for continuous per-tick forces like drag/wind
    (rather than one-shot impulses) and is what shows up correctly
    attributed in Sable's own force debug/visualization tooling.
    **Coordinate space (confirmed at M7 implementation time by
    decompiling `FloatingBlockController`'s own gravity/lift force
    calls)**: both `point` and `force` are in the sub-level's LOCAL
    frame, not world space — Sable's own code rotates a world-space
    vector (gravity) into local space via `Pose3dc.transformNormalInverse(...)`
    and passes the result straight to `recordPointForce`/
    `applyAndRecordPointForce` without ever transforming to world space.
    Likewise `MassData.getCenterOfMass()` is LOCAL (built directly from
    the same local block-iteration bounds `MassTracker.build` consumes).
    `AeronauticsWindForceApplier` mirrors this exactly: only the wind
    *direction* gets rotated (world→local via `transformNormalInverse`);
    the center-of-mass point is used as-is, with a `transformPosition`
    to world space done separately and only to read the contraption's
    world-space Y for `WindHeightScaling`.
  - **Tick hook**: `dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent`
    (the NeoForge-specific wrapper around the loader-agnostic
    `SablePrePhysicsTickEvent` interface) **extends
    `net.neoforged.bus.api.Event`** and is posted on the normal NeoForge
    event bus. Carries `getPhysicsSystem()`/`getTimeStep()` (the physics
    substep dt, which runs at a different cadence than our 1Hz
    `WindSimulator` — re-queue the force every physics tick from
    whatever `WindState` last computed; don't try to raise
    `WindSimulator`'s own cadence to match). **Correction from this
    file's earlier guess**: this can NOT be subscribed via the usual
    `@EventBusSubscriber`+static-`@SubscribeEvent` pattern used
    everywhere else in this codebase — that pattern gets scanned/
    registered by NeoForge at mod-init regardless of intent, which would
    force-classload this Sable event type even when Sable isn't
    installed, violating the isolation rule below. `AeronauticsWindForceApplier`
    instead registers manually
    (`NeoForge.EVENT_BUS.addListener(EventClass.class, this::handler)`)
    from inside `start()`, which itself only ever runs after the
    `ModCompat` check passed.
  - **Sub-level assembly hook**: `SubLevelContainer.addObserver(SubLevelObserver)`
    (registered from `ForgeSableSubLevelContainerReadyEvent`, also a
    real NeoForge `Event`) — `onSubLevelAdded(SubLevel)` fires once per
    assembled contraption and is where `AeronauticsWindForceApplier`
    does its one-time lift-block-ratio scan, mirroring exactly when
    Sable itself rebuilds `buildMassTracker()`.
  - **Block iteration for the local bounding box**: `ServerSubLevel.getPlot()
    -> ServerLevelPlot`, `.getBoundingBox() -> BoundingBox3ic` (from
    Sable Companion's `dev.ryanhcode.sable.companion.math` package,
    confirmed public with `minX()`/`maxX()`/etc.) gives the sub-level's
    LOCAL integer bounds — the same bounds `MassTracker.build` iterates.
    Sable itself wraps the level in a `dev.ryanhcode.sable.util.LevelAccelerator`
    (a chunk-caching `BlockGetter`) for this, but that class lives
    outside the `api` package; `AeronauticsWindForceApplier` uses the
    sub-level's plain `Level.getBlockState(BlockPos)` directly instead
    (a definitely-stable Minecraft API, and the perf difference is
    negligible since this scan happens once per assembly, not per tick).
  - **Bonus discovery — a native ambient-wind hook, but internal/unstable
    and currently a no-op everywhere**: `dev.ryanhcode.sable.api.SubLevelHelper.registerWindProvider(BiFunction<Vector3dc,
    Level, Vector3dc>)` lets a mod register itself as a source of
    ambient wind velocity at a position; `.getVelocityRelativeToAir(Level,
    Vector3dc, Vector3d)` is the read side. Decompiled (not just
    `javap`'d) to confirm the actual mechanism: `getVelocityRelativeToAir`
    computes the queried point's true physics velocity (via
    `RigidBodyHandle`'s linear+angular velocity, correctly accounting for
    rotation — a point on a spinning propeller arm reads faster than the
    hull's center of mass) and subtracts every registered provider's
    vector from it — a coherent, correctly-implemented relative-airspeed
    calculation, not leftover/vestigial code. However: **the whole
    `SubLevelHelper` class is annotated `@ApiStatus.Internal`**, unlike
    every other class this section relies on for force application
    (`SubLevelContainer`/`ServerSubLevelContainer`/`RigidBodyHandle`/
    `QueuedForceGroup`/`ForceGroup` — none of those carry that
    annotation) — the author is explicitly flagging it as not a
    committed public surface. Cross-jar grep across all four first-party
    jars (Sable, Simulated, Aeronautics, Offroad) found **zero call
    sites** for `getVelocityRelativeToAir`/`registerWindProvider`/
    `windProviders` anywhere except `SubLevelHelper` itself and one
    pass-through in `ActiveSableCompanion` — nothing today reads the
    result, so registering a provider would currently have zero visible
    effect on any vanilla Aeronautics/Offroad contraption; it isn't
    auto-wired into any drag/lift calculation. Asymmetric API surface:
    the *read* side is reachable through the stable, non-`@Internal`
    `SableCompanion.INSTANCE.getVelocityRelativeToAir(...)` (Companion
    just delegates to the internal method), but the *write* side
    (`registerWindProvider`) has no Companion equivalent — only reachable
    by calling the internal class directly. **Recommendation for M7**:
    register anyway (cheap, isolated to one call site in
    `AeronauticsWindForceApplier`, already wrapped in the try/catch
    CLAUDE.md's conventions call for, so a future rename/removal fails in
    one place already being defended) as free ecosystem citizenship and
    optionally as our own source of correct per-point relative-airspeed
    (via the stable Companion read side) instead of a center-of-mass
    approximation — but the actual force application must keep using the
    confirmed-stable `QueuedForceGroup` path regardless; this hook does
    **not** replace that, it only makes wind data queryable.
- Sable Companion: https://github.com/ryanhcode/sable-companion. Real
  API interface `dev.ryanhcode.sable.companion.SableCompanion`
  (previously only partially confirmed — `getContaining`,
  `isInPlotGrid`, `projectOutOfSubLevel`, `distanceSquaredWithSubLevels`
  — now also confirmed to include `getVelocity`/
  `getVelocityRelativeToAir`, `runIncludingSubLevels`/
  `findIncludingSubLevels`, and several entity/vehicle sub-level
  tracking helpers, via `javap` on Sable's own `ActiveSableCompanion`
  implementation). **Required at compile time regardless of whether
  AeroWeather calls it directly**: Sable's own public classes reference
  Companion types in their signatures (e.g. `SubLevel implements
  dev.ryanhcode.sable.companion.SubLevelAccess`), so
  `dev.ryanhcode.sable.companion.*` must be resolvable just to compile
  against `SubLevel`/`ServerSubLevel` at all. Maven:
  `https://maven.ryanhcode.dev/releases`, group
  `dev.ryanhcode.sable-companion`, artifact
  `sable-companion-common-1.21.1` (the loader-agnostic core module —
  separate sibling artifacts `sable-companion-1.21.1` and
  `sable-companion-fabric-1.21.1` exist for loader-specific wiring, not
  needed here). **Pinned to `1.6.0`**, not whatever's newest on the
  maven (`1.4.0` at time of writing) — confirmed via Sable 2.0.5's own
  `META-INF/jarjar/metadata.json` that it embeds exactly
  `sable-companion-common-1.21.1` version `1.6.0`, and its
  `neoforge.mods.toml` declares itself incompatible with any
  `sablecompanion` mod install newer than `1.6.0` ("Sable is out of
  date"), so matching that exact version avoids a binary mismatch.
- Prior art: "PMWeather Aeronautics compat" — integrates wind by caching
  a sub-level's exterior-surface-patch profile, sampling wind across
  exposed faces with a quadratic pressure model (force ∝ exposed area ×
  wind speed²), summing to net force + torque, and handing that to
  Sable's physics. Still the intended shape for M7's actual force
  model, adapted to the confirmed API above (AABB-based exposed area,
  not per-face, since no finer geometry API exists) and its
  `QueuedForceGroup.applyAndRecordPointForce` entrypoint.
- Modrinth Maven: `https://api.modrinth.com/maven` (wired in
  `build.gradle` via `exclusiveContent`/`includeGroup "maven.modrinth"`),
  coordinates `maven.modrinth:<slug>:<version id>` — confirmed working
  for `sable`/`create`/`create-aeronautics` (version ids above, pinned
  in `gradle.properties` as `sable_version`/`create_version`/
  `create_aeronautics_version`).
- NeoForge docs: https://docs.neoforged.net/

## Open questions / research spike tracker

Resolve these before relying on them — don't let assumptions calcify:

- ModDevGradle `2.0.144`'s exact `jarJar` DSL for embedding Sable
  Companion into AeroWeather's own published jar (M7 — not needed until
  AeroWeather actually ships; `implementation` is sufficient for dev
  builds/testing in the meantime).
- **Design decision made during M6, implemented as-is at M7 (not
  revisited)**: Sable's sub-level API is entirely content-agnostic —
  nothing distinguishes a Create Aeronautics airship from a Create
  Offroad truck or any other Sable-based contraption (no "this is an
  Aeronautics assembly" tag was found anywhere in the Sable or
  Aeronautics API surface). `AeronauticsWindForceApplier` applies wind
  force to **every** Sable sub-level uniformly and gates activation on
  **`sable`** being loaded, not specifically `aeronautics`. This does
  technically broaden "Create Aeronautics interaction" to "any Sable
  contraption interaction" in practice; kept here as a record of the
  decision, not because it's still open.

Resolved and confirmed working via jar inspection this session (M6),
kept here as a record:
- Real modIds and the module breakdown of the Create Aeronautics
  "bundled" jar — see "External references" above.
- Modrinth version ids for MC 1.21.1 NeoForge builds — see "External
  references" above.
- The real force/impulse entrypoint, tick hook, and the rest of the
  confirmed Sable API surface — see "External references" above. In
  particular, `SubLevelCollisionEvent` (this file's earlier guess for
  how contraption physics gets driven) **does not exist anywhere in the
  real API** — the actual entrypoint is `ServerSubLevel.getOrCreateQueuedForceGroup(...)
  .applyAndRecordPointForce(...)`.

Resolved during M7 implementation (the four items the pre-implementation
spike flagged as unconfirmed — see "External references" above for full
detail): `getCenterOfMass()`/`applyAndRecordPointForce`'s point+force
are all in the sub-level's LOCAL frame, not world space;
`ServerSubLevel.getPlot().getBoundingBox()` is fully public and
accessible (no fallback needed); `Vector3d`/`Vector3dc`/`Pose3dc` are
confirmed `org.joml.*`/Sable Companion types respectively; `Pose3dc`
exposes convenient `Vec3`-overloaded `transformPosition`/
`transformNormalInverse` methods directly (no need to go through
`.orientation()` manually). Compiled clean on the first attempt once
these were resolved.

Resolved and confirmed working via live testing (kept here as a record,
not because they're still open): `SavedData.Factory`'s deserializer
takes `(CompoundTag, HolderLookup.Provider)`, obtained via
`level.getDataStorage().computeIfAbsent(factory, name)`;
`PacketDistributor.sendToPlayer(player, payload)` and
`.sendToPlayersInDimension(level, payload)` for sync; and the
height-based wind scaling curve (exponent 0.3, 100-block reference
height, 3x max multiplier — see "Wind system design" above and
`AeroWeatherClientConfig`).
