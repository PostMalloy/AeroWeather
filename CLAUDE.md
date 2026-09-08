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

See "Out of scope" below for what this explicitly does *not* include.

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

Root package `com.postmalloy.aeroweather`. All milestones through M8 are
complete (see Roadmap); this table is the actual, current structure —
keep it in sync as code changes.

```
AeroWeather.java                      main @Mod class: registries, config, subsystem bootstrap
AeroWeatherClient.java                 client @Mod entry: config screen registration

config/
  AeroWeatherCommonConfig.java        drift rate, gust chance/magnitude, rain/thunder boost, sync thresholds, aeronautics force tuning
  AeroWeatherClientConfig.java        particle toggle, max count, spawn radius, outdoors-only flag, active-contraption gating

wind/
  WindDirection.java                  cardinal enum + degree/vector helpers; travelVector(bearingDeg) -> Vec3
  WindmillWindResponse.java           pure function: (facing, wind, strength) -> quantized Create windmill speed multiplier
  WindState.java                      direction/strength + drift target + gust + weather-boost + override; NBT I/O
  WindSavedData.java                  SavedData, one per ServerLevel via getDataStorage().computeIfAbsent(...)
  WindSimulator.java                  LevelTickEvent.Post @ 20-tick cadence: drift/gust/weather-boost/override
  WindOverride.java                   operator-set override value type
  WindHeightScaling.java              pure function: base strength -> elevation-scaled strength (power-law, 0 at/below sea level)

network/
  NetworkHandler.java                 RegisterPayloadHandlersEvent registration
  payload/ClientboundWindSyncPayload.java   record CustomPacketPayload: dimension id, directionDeg, strength
  payload/ClientboundActiveContraptionsPayload.java   record CustomPacketPayload: dimension id, List<Vec3> positions
                                       (Sable sub-levels currently experiencing wind force; broadcast from
                                       AeronauticsWindForceApplier, not WindSync - see M7 design section)
  ClientPayloadHandler.java           updates client.ClientWindState / client.ClientActiveContraptions on receipt
  WindSync.java                       decides when to broadcast: force (join/dimension-change/respawn/command) vs threshold+heartbeat
  PlayerSyncListener.java             PlayerLoggedInEvent/PlayerChangedDimensionEvent/PlayerRespawnEvent -> WindSync.sendTo(player)

client/
  ClientWindState.java                client-side cache of latest synced wind per dimension
  ClientActiveContraptions.java       client-side cache of latest synced active-contraption positions per dimension
  ClientActiveWindmills.java          client-side set of Create windmills the wind is currently turning; self-reported
                                       by the windmill mixin (no networking), entries age out after 20 ticks
  particle/
    WindStreakParticle.java           TextureSheetParticle + nested Provider; shared by WIND_STREAK/WIND_GUST/WIND_LOOP
    AeroWeatherParticleProviders.java RegisterParticleProvidersEvent registration; also overrides vanilla's
                                       campfire smoke/cherry leaves providers (see below)
    WindParticleSpawner.java          ClientTickEvent.Post: spawns particles around player from ClientWindState
    AmbientWindDrift.java             pure function: wind-driven target velocity at a Y, for vanilla particle overrides below
    WindDriftingCampfireSmokeParticle.java  extends vanilla CampfireSmokeParticle, eases toward AmbientWindDrift's target
    WindDriftingCherryParticle.java   extends vanilla CherryParticle (cherry_leaves), same wind-easing treatment

registry/
  AeroWeatherParticles.java           DeferredRegister<ParticleType<?>>: WIND_STREAK, WIND_GUST, WIND_LOOP
  AeroWeatherCommandArgumentTypes.java  DeferredRegister<ArgumentTypeInfo<?,?>>: registers DirectionArgument for command-tree sync

command/
  AeroWeatherCommand.java             /aeroweather wind <direction> <strength> | reset | info  (op-level)
  DirectionArgument.java              ArgumentType<Float>: cardinal keyword OR degree float

integration/
  ModCompat.java                      ModList.get().isLoaded(modId) + SABLE_MODID constant; zero Sable imports
  aeronautics/
    AeroWeatherBlockTags.java         TagKey<Block> ENVELOPE/LEVITITE/WINDMILL_SAILS; pure data tags, always safe to classload
    WindForceApplier.java             interface: start(IEventBus modEventBus) — not a per-tick method; the real
                                       applier drives itself off Sable's own physics-tick event once started
    NoopWindForceApplier.java         start() no-op
    AeronauticsWindForceApplier.java  ONLY class allowed to reference Create/Aeronautics/Sable types directly
    AeronauticsIntegration.java       static get(); resolve() checks ModCompat.isLoaded("sable") BEFORE
                                       constructing AeronauticsWindForceApplier, catch(Throwable) -> Noop
  create/
    CreateWindmillWind.java           side-aware wind lookup behind the windmill mixin; zero Create types,
                                       always safe to classload (see M9)

mixin/
  AeroWeatherMixinPlugin.java         IMixinConfigPlugin; getMixins() withholds the windmill mixin unless
                                       LoadingModList says "create" is installed
  WindmillBearingBlockEntityMixin.java  wind-scales Create windmill speed; references Create only by
                                       string target/descriptor, never by type (see M9)
```

**Config screen labels**: NeoForge's built-in `ConfigurationScreen` looks
up each leaf value's display name as `<modid>.configuration.<key>` — the
*bare* key, with **no section prefix** (only the section buttons
themselves, e.g. `drift`/`aeronautics`/`particles`, use their own bare
name). Falls back to printing the raw key when no lang entry exists, so
every `push(...)` section and `define*(...)` leaf across both config
classes needs a matching flat `en_us.json` entry, and a leaf's key must
stay unique mod-wide (section context isn't part of its lookup).

Particle textures live at
`assets/aeroweather/textures/particle/windparticle{1-8}.png` (declared
in `wind_streak.json`), `gustparticle{1-8}.png` (declared in
`wind_gust.json`, the `WIND_GUST` type — spawned at half of
`WIND_STREAK`'s rate by `WindParticleSpawner`, once elevation-adjusted
strength exceeds `AeroWeatherClientConfig.GUST_PARTICLE_MIN_STRENGTH`), and
`loopparticle{1-8}.png` (declared in `wind_loop.json`, the `WIND_LOOP`
type — spawned by the exact same always-on accumulator formula as
`WIND_STREAK`, but each trigger only actually spawns on a 50% coin
flip, a rarer companion texture for visual variety, not an additional
full-rate stream). All three share `WindStreakParticle`'s
class/rendering entirely, just a different `SpriteSet`.
`WindStreakParticle` cycles its 8 frames exactly once over
its own lifetime via vanilla `TextureSheetParticle#setSpriteFromAge`
(not a fixed per-frame duration, which looped 1.5-2.5x before a
particle disappeared and read as stuttery — `setSpriteFromAge` always
spans the full frame set over `[0, lifetime]` regardless of what
lifetime is, so "exactly one cycle" holds no matter how lifetime is
computed). Lifetime itself (12-40 ticks, plus jitter) is derived from
wind speed, recovered from the constructor's `xSpeed`/`zSpeed` since
`ParticleProvider#createParticle`'s fixed vanilla signature has no room
for an explicit strength parameter — velocity magnitude already equals
`WindParticleSpawner`'s locally-adjusted drift speed exactly, since
travel direction is a unit vector. Stronger wind means a shorter life
and thus a faster-cycling flipbook. It bypasses `TextureSheetParticle`'s 7-arg constructor
(chains to vanilla `Particle`'s randomizing constructor, which jitters
velocity and adds +0.1 to `yd`) and sets `xd`/`yd`/`zd` directly instead.

Orientation is a **fixed world-space card**, not the default
camera-billboard — computed once at spawn from the travel direction
(normal perpendicular to travel) and never re-derived from the camera,
so the texture reads face-on across the wind and edge-on looking up/
downwind. Two things layer on top:
- **Double-sided rendering**: drawn twice with reversed winding, since
  `PARTICLE_SHEET_TRANSLUCENT` doesn't set an explicit cull state and a
  fixed (non-billboard) card can end up back-face-out from some angles.
- **Left/right mirroring**: since every particle shares one fixed
  orientation, a "left of upwind player" particle and its mirror
  counterpart would otherwise share texture handedness instead of
  reading as a pair — computed once at spawn via a cross-product sign,
  applied by swapping `getU0()`/`getU1()`.

Particles spawn at a random angle around the player (they drift toward
the travel direction regardless of spawn angle, so this only affects
ambience); a per-particle ±5° direction jitter avoids uniform drift.
Both spawn rate and drift speed scale with wind strength; vertical
speed is clamped to 0.

## Wind system design

- Per-`ServerLevel` state stored in `SavedData` (naturally per-dimension —
  `isRaining()` is always false in the Nether/End, so weather-boost is
  always 0 there with no special-casing needed).
- Effective strength = `overridden ? overrideStrength : min(clamp(baseStrength
  + gustStrength + weatherBoost, 0, 100), currentStrengthCap())`.
- Natural drift: periodically re-roll a target direction/strength within
  a bounded delta, lerp current value toward it each simulation step.
- Gusts: short probabilistic additive spikes that decay, layered on top
  of the drift value.
- Elevation scaling (`wind/WindHeightScaling.java`, a standalone pure
  function): approximates the real-world wind profile power law — 0 at
  or below sea level, reaching base strength at a configurable reference
  height (`HEIGHT_REFERENCE_ABOVE_SEA_LEVEL`, default 100 blocks), capped
  at a configurable multiplier (`HEIGHT_MAX_MULTIPLIER`, default 3x).
  Kept standalone (not baked into `WindState`) since `WindParticleSpawner`
  (client, player's Y), `AeronauticsWindForceApplier` (server, a
  contraption's center-of-mass Y), and `AmbientWindDrift` (client, a
  vanilla particle's own live Y — see "Ambient particle wind" below)
  all need to sample wind at an arbitrary position, not just the
  ambient per-dimension value. Curve
  parameters live in the *common* config (not client) since the server-side
  `/aeroweather wind info` command and the aeronautics force both need them.
- `weatherBoost` eases (doesn't snap) toward 0/rain-boost/thunder-boost
  based on `level.isRaining()`/`isThundering()`, so weather starting/
  stopping never jump-cuts wind.
- Natural (non-overridden) strength is capped per weather tier via
  `currentStrengthCap()`: `STRENGTH_CAP_CLEAR`/`_RAIN`/`_THUNDER`
  (defaults 50/75/100) — a maximum, not a fixed value. The drift
  *target* itself is bounded by the same cap in `tickDrift`, not just
  the final sum: capping only the final sum let `baseStrength`'s random
  walk wander above the cap (only clamped to [0,100]) and then sit
  pinned there until it happened to drift back down, which read as
  "stuck at a constant" rather than "capped but still varying." This is
  separate from and upstream of `WindHeightScaling`'s elevation
  adjustment, which can still push the elevation-adjusted value above
  the tier cap. Overridden strength ignores the cap entirely.
- Command override pins an absolute direction/strength and **freezes**
  natural drift while active; `reset` resumes drift from wherever it was.
- Simulation runs at ~1 Hz (every 20 ticks, gated inside `LevelTickEvent.Post`
  — NeoForge has no built-in tick-filtered event), not every tick.
- Server→client sync (`CustomPacketPayload` via `RegisterPayloadHandlersEvent`):
  immediate on player join/dimension-change/respawn and command
  overrides; otherwise only on a delta threshold (direction Δ > 2°,
  strength Δ ≥ 1) or a ~5s heartbeat — never every tick.

## Ambient particle wind

Wind also nudges two *vanilla* particles — campfire smoke
(`campfire_cosy_smoke`/`campfire_signal_smoke`) and falling cherry
leaves (`cherry_leaves`; 1.21.1 has no other vanilla falling-leaves
particle type, e.g. Pale Garden's pale oak leaves weren't added yet) —
via `WindDriftingCampfireSmokeParticle`/`WindDriftingCherryParticle`
(`client/particle/`), each a thin subclass of the corresponding vanilla
particle class registered *over* vanilla's own provider in
`AeroWeatherParticleProviders`. This is a supported override, not a
hack: `ParticleEngine`'s provider map is a plain `HashMap`, vanilla's
own bootstrap registration runs before `RegisterParticleProvidersEvent`
fires, and `registerSpriteSet` simply overwrites the map entry — the
standard mechanism mods use to reskin/modify an existing vanilla
particle. Both vanilla particle classes have `protected` constructors
(subclassable across packages) and `protected xd`/`zd` velocity fields
(inherited from `Particle`, accessible from actual subclass code via
`this`) — confirmed by decompiling the real classes rather than
guessing, since `protected` access rules are easy to get subtly wrong
across packages (e.g. vanilla's own nested `Provider` classes can call
`particle.setAlpha(...)` only because they live in the *same package*
as `Particle`; our providers, in a different package, must do that
setup inside the particle subclass's own constructor instead).

Each subclass overrides `tick()` to ease `xd`/`zd` a small fixed
fraction (`EASE_FACTOR = 0.02`) toward a wind-driven target velocity
each tick, then calls `super.tick()` unchanged — the same "ease toward
a target, don't snap" idiom `WindState` already uses for
`weatherBoost`, chosen specifically to avoid unbounded runaway drift
(a per-tick *additive* delta, mirroring how vanilla's own campfire-smoke
jitter or cherry-leaf curl accumulate, would grow without bound over a
non-zero-mean push since those particles live 80–330 ticks). The target
velocity itself — `AmbientWindDrift.targetVelocity(level, y)` — is a
pure function (no `Particle` field access, so no protected-access
constraint) computed fresh every tick: elevation-adjusted strength via
`WindHeightScaling.scale(...)` at the particle's own **live** Y (not a
value cached at spawn — matches `AeronauticsWindForceApplier` sampling
a contraption's own current position rather than the player's) times
`WindDirection.travelVector(...)`, scaled by
`AeroWeatherClientConfig.AMBIENT_WIND_PARTICLE_INTENSITY` (target extra
drift speed in blocks/tick at strength 100; default 0.2, 0 disables the
effect entirely — the single fine-tuning knob this feature exposes).
Returns `null` (skip this tick) when disabled, unsynced for the
particle's dimension, or the elevation-adjusted strength there is 0.

## M7: Aeronautics wind force

`AeronauticsWindForceApplier` (`integration/aeronautics/`) applies real
force to every Sable sub-level's center of mass, every physics tick.
Gated purely on `sable` being loaded (see "Open questions" for why) — a
no-op with zero Sable classloading when it isn't.

Force formula and where each input comes from:

| Input | Source | Cached or per-tick |
|---|---|---|
| Sectional area | Closed-form box-silhouette formula (`4 * (hx*hy*abs(dz) + hy*hz*abs(dx) + hx*hz*abs(dy))`) against cached local half-extents + a fresh per-tick local wind direction | Half-extents cached once; direction/area recomputed every physics tick (vector math only, no block iteration) |
| Lift ratio | `(lift-tagged blocks) / (total non-air blocks)`, one-time scan over the sub-level's local plot bounds | Cached once, **never refreshed** on later block edits (mirrors Sable's own `buildMassTracker()`) — but computed **lazily on first force application, not eagerly in `onSubLevelAdded`**: that notification fires before a contraption's blocks are actually copied into the sub-level's storage (an eager scan there permanently caches an empty snapshot — confirmed live). By the time a sub-level reaches `applyWindForce` it's genuinely running physics ticks, so its blocks are populated. `onSubLevelAdded` isn't overridden at all; only `onSubLevelRemoved` is, to evict the cache entry |
| Wind strength | `WindSavedData.get(level).wind().strength()` → `WindHeightScaling.scale(...)` sampled at the contraption's own center-of-mass world Y | Wind state updates ~1Hz; height-scaled value and force recomputed every physics tick |
| Force direction | `WindDirection.travelVector(bearingDeg)`, rotated into the sub-level's local frame via `Pose3dc.transformNormalInverse(...)` | Recomputed every physics tick |
| Application point | The sub-level's local-space center of mass, used as-is (not transformed to world space — see "External references") | Read fresh every tick |

`magnitude = pressureCoefficient * sectionalArea * strength² * liftRatio
* oscillation`, clamped to `AERONAUTICS_MAX_FORCE` — a single scalar
multiplier, not a separate vertical lift force. 0 lift-tagged blocks →
0 force, a cheap early-exit before mass/strength/direction are even
touched. `oscillation = 1 + oscillationAmplitude * sin(2π *
(gameTime/20s) / oscillationPeriodSeconds)` — a ripple (default ±60%
every 2s) phased off game time, not accumulated substep dt, so it's
stable regardless of substep count. `pressureCoefficient` (default
`0.00001`) and `oscillationAmplitude` (default `0.6`) were both tuned
against how the force actually felt on a real assembled hot air
balloon, not derived analytically.

Lift blocks are the union of three pre-existing block tags —
`#aeronautics:envelope` (balloon fabric), `#aeronautics:levitite`
(magic floating rock), `#create:windmill_sails` (Create's sail blocks,
itself including `#minecraft:wool`) — pure data, referenced via
`AeroWeatherBlockTags`' `TagKey<Block>` constants with zero compile
dependency on Aeronautics/Create Java classes.

`WIND_FORCE_GROUP` is registered into Sable's `ForceGroups.REGISTRY` via
a standard `RegisterEvent` listener on the mod event bus — required
(see "External references") because an unregistered `ForceGroup` has no
registry id, and Create Aeronautics' contraption diagram tool crashes
the client connection trying to network-encode one. Registering also
makes the force show up correctly on that diagram (`defaultDisplayed = true`).

**Proximity-gated particles** (client-only opt-in, off by default):
`WindParticleSpawner` can suppress ambient wind particles unless the
player is within `ACTIVE_CONTRAPTION_RADIUS` of something the wind is
*currently* acting on, gated by `RESTRICT_TO_ACTIVE_CONTRAPTIONS`. Two
independent sources feed that check (see also M9's windmill half):
- A Sable sub-level currently experiencing nonzero wind force. This
  reuses the same set the 0-lift-blocks hard constraint already produces
  — a contraption with no lift blocks (or zero effective force this
  tick) never enters it, since a position is only recorded when
  `applyWindForce` actually reaches `applyAndRecordPointForce`.
- A Create windmill the wind is currently turning (`ClientActiveWindmills`).

Both config keys kept their original `restrictToActiveContraptions` /
`activeContraptionRadius` names when windmills were folded in — renaming
them would silently reset existing users' settings, so only the `en_us`
display strings were broadened.

Plumbing: `AeronauticsWindForceApplier` collects active world-space
positions (the same `comWorld` already computed for `WindHeightScaling`
sampling) into a per-level list, broadcast via a **separate**
`LevelTickEvent.Post` listener on a fixed 5-tick cadence using
`ClientboundActiveContraptionsPayload` — deliberately decoupled from
Sable's own (much faster) physics-substep cadence, and kept separate
from `ClientboundWindSyncPayload`/`WindSync` since positions and wind
state have very different natural update rates. Always broadcasts, even
an empty list, so stale positions clear once nothing is active.
Client-side, `ClientActiveContraptions` mirrors `ClientWindState`'s
shape. No join/dimension-change immediate-sync path — the 5-tick
periodic broadcast alone closes that gap fast enough for a
particle-cosmetics feature.

**Performance**: the position list is only *rebuilt* on physics
substeps aligned with that same 5-tick cadence
(`beginPositionRecordingIfDue`, tracked per-level via
`lastPositionRecordTickByLevel` so multiple substeps within one
recording tick don't rebuild it repeatedly) — not every substep, since
Sable's substep rate can exceed 20Hz and nothing reads the list between
broadcasts. `applyWindForce` takes a nullable `activePositions` list and
just skips recording when null; force application itself still runs
every substep regardless.

## M9: Create windmill wind response

Wind drives **Create windmill bearings**: their normal sail-count speed
is multiplied by a signed factor derived from the wind at the windmill's
own position. Gated on `create` being loaded, with no effect otherwise.

`wind/WindmillWindResponse.java` is the pure function (config-free,
params passed in, mirroring `WindHeightScaling`):

```
strengthFactor = min(adjustedStrength / fullSpeedStrength, maxSpeedMultiplier)
intoFront      = -dot(windTravelUnit, facingUnit)   # +1 = straight into the front face
angle          = acos(intoFront)                    # 0..180
offAxis        = min(angle, 180 - angle)            # 0..90
magnitude      = offAxis <= 45 ? 1 : lerp(1, minDirectionalScale, (offAxis - 45) / 45)
directionFactor = (angle > 90 && reverseWhenBehind) ? -magnitude : magnitude
multiplier     = quantize(strengthFactor * directionFactor)
```

- The windmill's **front face normal is its block's
  `BlockStateProperties.FACING`** — Create's `BearingBlock` extends
  `DirectionalKineticBlock`, whose `FACING` *is* the vanilla property
  instance, and it points from the bearing toward the sails (confirmed
  via `hasShaftTowards` returning `FACING.getOpposite()`). So facing is
  readable with zero Create imports.
- **Vertical-axis windmills (facing UP/DOWN) skip the directional factor
  entirely.** Horizontal wind is always perpendicular to their axis, so
  applying the rule would peg every horizontal-rotor build at
  `minDirectionalScale` (0 by default) permanently.
- **Quantized to 0.05 steps.** This is load-bearing, not a nicety:
  Create's `updateGeneratedRotation()` runs `detachKinetics()` /
  `setSpeed()` / `attachKinetics()` plus a stress recalc and a block
  entity resync — a full kinetic-network rebuild across every connected
  shaft. Only a coarse step function of the continuously drifting wind
  may drive it. Quantizing also keeps the client's and server's
  independently computed factors agreeing despite the sync thresholds.
- `AeroWeatherCommonConfig`'s `windmills` section is **common, not
  client**: the client computes the same multiplier to spin the sails
  visually (see below), so both sides must read identical values.

### Why it must run on both sides

`MechanicalBearingBlockEntity.getAngularSpeed()` is
`convertToAngular(isWindmill() ? getGeneratedSpeed() : getSpeed())` —
for a windmill the *visual* rotation comes from `getGeneratedSpeed()`,
not the synced network speed, and it runs client-side too. So
`CreateWindmillWind.speedMultiplier(...)` reads `WindSavedData` on the
server and `ClientWindState` on the client. That same method zeroes out
when `getSpeed() == 0`, which is what makes scale-to-0 genuinely stop a
windmill rather than just slowing it.

Dropping to 0 is safe from auto-disassembly:
`WindmillBearingBlockEntity.onSpeedChanged` saves and restores
`assembleNextTick`, deliberately cancelling the `assembleNextTick = true`
that `MechanicalBearingBlockEntity.onSpeedChanged` sets. Sign flips call
`contraption.stop(level)` only when `prevSpeed != 0` — and with the
default `minDirectionalScale = 0` the windmill always passes *through*
zero before reversing, so reversal is naturally smooth. That only stops
being true if the floor is raised above 0.

### The mixin (the codebase's only one)

There is **no Create API for generated speed** — `api/event/` has only
`BlockEntityBehaviourEvent`/`PipeCollisionEvent`/`TrackGraphMergeEvent`,
and `api/stress/BlockStressValues.RPM` is a display/tooltip registry.
Manipulating sail count instead was rejected: Create clamps to `min 1`
RPM, so it can never reach 0. A mixin is the only mechanism.

`mixin/WindmillBearingBlockEntityMixin.java` references Create **only by
string target and descriptor, never by type** — so Create stays
`localRuntime`, never a compile dependency (compiling against
`WindmillBearingBlockEntity` would drag catnip/registrate/ponder/flywheel
onto the compile classpath). This works because the one Create method it
needs, `updateGeneratedRotation()`, has a Create-free `()V` descriptor;
`running` is a plain `protected boolean`; and position/facing come from
vanilla `BlockEntity` via the standard `(BlockEntity) (Object) this`
cast. **Don't "tidy" this into typed references.**

**`@Shadow` only reaches members declared on the target class itself.**
Mixin resolves shadowed *fields* against `WindmillBearingBlockEntity`
alone, not its superclasses. Shadowing `running` (declared on
`MechanicalBearingBlockEntity`) compiled fine and then crashed the client
at class-transform time with "@Shadow field running was not located in
the target class" — a `javap` check that confirms a member exists must be
run against the *target* class, not its parents. The members declared
directly on the target, and therefore safe to shadow or inject into, are:
`updateGeneratedRotation()`, `getGeneratedSpeed()`,
`getAngleSpeedDirection()`, `tick()`, `onSpeedChanged(float)`,
`lastGeneratedSpeed`, `movementDirection`, `queuedReassembly`.

Two injections:
- `@ModifyExpressionValue` on the **`getAngleSpeedDirection()` call
  inside `getGeneratedSpeed()`** — deliberately not `@At("RETURN")`.
  `getGeneratedSpeed()` returns a cached `lastGeneratedSpeed` early when
  the contraption entity is detached, and `updateGeneratedRotation()`
  fills that cache *from `getGeneratedSpeed()` itself* — already scaled.
  Scaling at RETURN would compound the factor on every update while a
  windmill sat detached. Modifying the ±1 direction term only ever
  touches the live sail-count branch.
The same tick injection also feeds the particle proximity gate: on the
**client** it reports the bearing's position into `ClientActiveWindmills`
whenever generated speed is nonzero. Windmills need no networking for
this (unlike Sable sub-levels, which must be broadcast from the server)
because a windmill bearing is an ordinary block entity that ticks
client-side too. Entries are timestamped and age out after 20 ticks
rather than being explicitly removed, so a windmill that stops turning,
unloads, or is broken drops out on its own with no removal hooks.

- `@Inject` at **HEAD of `tick()`** (not TAIL — `tick()` returns early in
  several places): recompute the factor on both sides; on the server,
  compare `getGeneratedSpeed()` against the last value pushed and call
  `updateGeneratedRotation()` only when it moved. Comparing the generated
  speed rather than the raw factor tracks exactly what Create consumes,
  and is 0 for an unassembled bearing, so idle windmills never push.

`@ModifyExpressionValue` is MixinExtras, which NeoForge already bundles
via jarJar — hence `compileOnly "io.github.llamalad7:mixinextras-neoforge"`
pinned to the version NeoForge ships. Note the group id is
`io.github.llamalad7` but the **package is `com.llamalad7`**.

`aeroweather.mixins.json` declares an **empty `mixins` array** on
purpose; `AeroWeatherMixinPlugin.getMixins()` supplies it, returning the
windmill mixin only when `LoadingModList.get().getModFileById("create")`
is non-null. Naming it in the JSON would make Mixin resolve the target
class at config-prepare time and fail outright when Create is absent.
The plugin spells the modid out itself rather than importing
`ModCompat`, so nothing pulls `ModList` onto the classloader that early.

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
  types. `AeronauticsIntegration.get()` checks `ModCompat.isLoaded(...)`
  *before* that class is ever classloaded — never let the JVM attempt to
  resolve an external class reference without a `ModList` check first.
  Event registration inside that class is deliberately manual/instance-
  based (`NeoForge.EVENT_BUS.addListener(...)` / `modEventBus.addListener(...)`),
  **not** this codebase's usual `@EventBusSubscriber` + static
  `@SubscribeEvent` pattern — that pattern gets scanned/registered by
  NeoForge at mod-init regardless of intent, which would force-classload
  Sable's event types even when Sable isn't installed. Do not "clean
  this up" to the usual annotation pattern.
- **Second sanctioned exception (M9)**: `mixin/WindmillBearingBlockEntityMixin`
  targets a Create class. It doesn't break the rule above because it
  names Create purely by *string* target/descriptor and never by type,
  and because `AeroWeatherMixinPlugin` withholds it entirely when Create
  is absent — a gate that fires before classload, so it's strictly
  stronger than the `ModCompat` runtime check. Its helper,
  `integration/create/CreateWindmillWind`, contains no Create references
  at all and is always safe to classload.
- Build dependency pattern: Sable needs `compileOnly`+`localRuntime`
  (it's the actual API surface used — Sable's sub-level API is
  content-agnostic, so AeroWeather doesn't need Create/Create Aeronautics
  as a compile dependency at all, only `localRuntime` for dev-client
  testing). Sable Companion's `sable-companion-common-1.21.1` module is
  `implementation` (designed to be embedded, ships safe no-op defaults;
  the `jarJar` half — embedding it into AeroWeather's own published jar
  — is still open, see "Open questions").
- `neoforge.mods.toml` declares Create and Sable (not Aeronautics — not
  linked against; not Sable Companion — embedded, not an optional
  install) as `type="optional"`, `ordering="AFTER"` dependencies.
  **Gotcha**: `versionRange=""` (intending "no minimum") is NOT parsed
  by NeoForge as unconstrained — it's an impossible range that rejects
  every installed version, crashing load with "Mod aeroweather only
  supports create/sable" even with both correctly installed.
  `versionRange="[0,)"` is the correct way to say "any version".
- Because Sable's internal physics API carries no third-party stability
  guarantee, the actual force-application call is wrapped defensively in
  two tiers: a systemic failure (e.g. during a lift-profile build) sets
  a global `disabled` flag and stops all further work; a per-sub-level
  failure during force application just skips that one sub-level for
  the tick (a single malformed contraption shouldn't take the whole
  feature down).

## Out of scope (for now)

Not building yet, don't add speculative abstractions for these — YAGNI,
but don't actively make future extension harder either:

- New weather pattern types (tornadoes, custom storms, etc.)
- New blocks/items (wind vanes, anemometers, etc.)
- Anything beyond wind simulation + its Create/Create Aeronautics
  interaction (windmill response and contraption force are in scope;
  other Create kinetic generators are not)

## Roadmap / milestones

M0–M8 are complete — repo scaffolding, wind simulation core, networking
sync, the `/aeroweather` command, particles, config, the Create
Aeronautics/Sable research spike (M6), the force-application
implementation (M7, see "M7: Aeronautics wind force" above), and live
integration testing against a real assembled airship (M8, which found
and fixed two real bugs: the `onSubLevelAdded` lift-ratio timing issue
and the contraption-diagram disconnect from an unregistered
`ForceGroup` — both documented above/in "External references"). See
`git log` for detailed history of each milestone.

**M9 (Create windmill wind response, see the section above) is written
and compile-verified, but NOT yet live-tested.** Unlike every earlier
milestone, compiling proves less here: a mixin's injection points are
only resolved at class-transform time. What *has* been verified
statically, against Create 6.0.10's actual bytecode: `getGeneratedSpeed()`
really does `invokevirtual getAngleSpeedDirection:()F`, and
`tick()`/`updateGeneratedRotation()`/`running` all exist with the
descriptors the mixin shadows. Still unverified until someone runs it:
that Mixin applies cleanly in a dev client, that windmill speed/direction
behave as intended in-game, and that a **without-Create** launch still
boots (the config-plugin gate).

## External references

- Create: real modId `create`. Modrinth project `create` (id
  `LNytGWDc`). Not a compile dependency — `localRuntime` only.
- Create Aeronautics ("Simulated Project"):
  https://github.com/Creators-of-Aeronautics/Simulated-Project. Ships as
  a single "bundled" jar (Modrinth project `create-aeronautics`, id
  `oWaK0Q19`) using NeoForge's `lowcodefml` jar-in-jar container to wrap
  three independently-modId'd mods:
  - `simulated` (Create Simulated, pkg `dev.simulated_team.simulated`) —
    core assembly/redstone blocks; its `api` package is block/sound
    helpers, not the physics API (that's Sable's).
  - `aeronautics` (Create Aeronautics proper, pkg `dev.eriksonn.aeronautics`)
    — propellers, hot air balloons, levitite.
  - `offroad` (Create Offroad, pkg `dev.ryanhcode.offroad`) — land vehicles.
  None of these three expose the physics API AeroWeather needs — that's
  entirely Sable's, so AeroWeather needs no compile dependency on Create
  or Create Aeronautics at all, only `localRuntime` for dev-client testing.
- Sable: https://github.com/ryanhcode/sable — the Rapier-based physics
  engine underneath. Real modId `sable`. Modrinth project `sable` (id
  `T9PomCSv`). Package root `dev.ryanhcode.sable`. This is AeroWeather's
  actual compile dependency (`compileOnly`+`localRuntime`). Confirmed API
  surface (`dev.ryanhcode.sable.api`, decompiled directly from the real
  jar):
  - **Enumeration**: `SubLevelContainer.getContainer(ServerLevel) ->
    ServerSubLevelContainer`, `.getAllSubLevels() -> List<ServerSubLevel>`
    (the live backing list, not a copy per call), or
    `.queryIntersecting(BoundingBox3dc)` for a spatial query.
  - **Geometry/pose**: `SubLevel.boundingBox() -> BoundingBox3dc`
    (global-space AABB — no finer per-face/exterior-surface API exists
    anywhere in Sable's/Simulated's/Aeronautics's jars), `.logicalPose()`/
    `.lastPose() -> Pose3d` (position + orientation).
  - **Mass**: `ServerSubLevel.getMassTracker() -> MassData` (`getMass()`,
    `getCenterOfMass()`, inertia tensor).
  - **Force application**: `ServerSubLevel.getOrCreateQueuedForceGroup(ForceGroup)
    -> QueuedForceGroup`, then `.applyAndRecordPointForce(Vector3dc point,
    Vector3dc force)`. **Both `point` and `force` are in the sub-level's
    LOCAL frame, not world space** (confirmed by decompiling
    `FloatingBlockController`'s own gravity/lift force calls — they
    rotate a world-space vector into local space via
    `Pose3dc.transformNormalInverse(...)` and pass the result straight
    through, never transforming to world space). Likewise
    `MassData.getCenterOfMass()` is LOCAL. `ForceGroup` is a plain record
    (`Component name, Component description, int color, boolean
    defaultDisplayed`) — constructing one directly doesn't need Veil,
    but an *unregistered* one crashes Create Aeronautics' contraption
    diagram tool (NullPointerException encoding `simulated:diagram_data`,
    which network-encodes a sub-level's force groups by registry id and
    finds none). Fix: register it into `ForceGroups.REGISTRY` via the
    standard NeoForge `RegisterEvent` pattern
    (`event.register(ForceGroups.REGISTRY_KEY, id, supplier)`), which
    also still needs zero Veil (only the built-in constants like
    `ForceGroups.DRAG` are Veil-typed; `REGISTRY`/`REGISTRY_KEY` are
    plain vanilla `Registry`/`ResourceKey`). Lower-level alternatives
    exist (`RigidBodyHandle.applyImpulseAtPoint`, raw
    `PhysicsPipeline.applyImpulse`) but `QueuedForceGroup` is built for
    continuous per-tick forces and is what shows up correctly in Sable's
    own force debug/visualization tooling.
  - **Tick hook**: `dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent`
    extends `net.neoforged.bus.api.Event`, posted on the normal NeoForge
    bus. Carries `getPhysicsSystem()`/`getTimeStep()` — fires at physics
    substep rate, different from (and usually faster than) the 20Hz game
    tick, so re-queue force every physics tick rather than trying to
    raise `WindSimulator`'s own 1Hz cadence to match.
  - **Sub-level assembly hook**: `SubLevelContainer.addObserver(SubLevelObserver)`
    (registered from `ForgeSableSubLevelContainerReadyEvent`, also a
    real NeoForge `Event`) — `onSubLevelAdded`/`onSubLevelRemoved` fire
    once per assembled/disassembled contraption. `onSubLevelAdded` fires
    *before* blocks are copied into storage, so it's unusable for a
    block-content scan (see the M7 design section's lift-ratio row).
  - **Block iteration for the local bounding box**: `ServerSubLevel.getPlot()
    -> ServerLevelPlot`, `.getBoundingBox() -> BoundingBox3ic` (from
    Sable Companion's `dev.ryanhcode.sable.companion.math` package) gives
    the sub-level's LOCAL integer bounds — confirmed to be the *tight*
    content bounds (unioned from loaded chunks' real block bounds), not
    Sable's much larger theoretical plot allocation. Sable itself wraps
    the level in `dev.ryanhcode.sable.util.LevelAccelerator` (a
    chunk-caching `BlockGetter`) for this; that class lives outside the
    `api` package, so AeroWeather uses plain `Level.getBlockState(BlockPos)`
    instead (negligible perf difference for a one-time-per-contraption scan).
  - **Ambient-wind hook (not used)**: `SubLevelHelper.registerWindProvider(...)`/
    `.getVelocityRelativeToAir(...)` is a real, correctly-implemented
    relative-airspeed mechanism, but the whole `SubLevelHelper` class is
    `@ApiStatus.Internal` and has zero call sites anywhere in Sable,
    Simulated, Aeronautics, or Offroad — not auto-wired into any drag/lift
    calculation today. Not worth depending on for the core force path;
    not currently used.
- Sable Companion: https://github.com/ryanhcode/sable-companion. Real
  API interface `dev.ryanhcode.sable.companion.SableCompanion`.
  **Required at compile time regardless of whether AeroWeather calls it
  directly** — Sable's own public classes reference Companion types in
  their signatures (e.g. `SubLevel implements SubLevelAccess`), so
  `dev.ryanhcode.sable.companion.*` must be resolvable just to compile
  against `SubLevel`/`ServerSubLevel` at all. Maven:
  `https://maven.ryanhcode.dev/releases`, group
  `dev.ryanhcode.sable-companion`, artifact
  `sable-companion-common-1.21.1` (the loader-agnostic core module).
  **Pinned to `1.6.0`**, not whatever's newest — confirmed via Sable
  2.0.5's own `META-INF/jarjar/metadata.json` that it embeds exactly
  that version, and its `mods.toml` declares itself incompatible with
  any newer `sablecompanion` install.
- Prior art: "PMWeather Aeronautics compat" — wind via a sub-level's
  exterior-surface-patch profile, quadratic pressure model (force ∝
  exposed area × wind speed²), net force+torque handed to Sable's
  physics. The shape M7's force model follows, adapted to the confirmed
  API (AABB-based exposed area via the box-silhouette formula, not
  per-face, since no finer geometry API exists).
- Modrinth Maven: `https://api.modrinth.com/maven` (wired via
  `exclusiveContent`/`includeGroup "maven.modrinth"`), coordinates
  `maven.modrinth:<slug>:<version id>`, pinned in `gradle.properties`.
- NeoForge docs: https://docs.neoforged.net/

## Open questions / research spike tracker

- ModDevGradle `2.0.144`'s exact `jarJar` DSL for embedding Sable
  Companion into AeroWeather's own published jar — not needed until
  AeroWeather actually ships; `implementation` is sufficient for dev.
- **Design decision (M6, implemented as-is at M7, not revisited)**:
  Sable's sub-level API is content-agnostic — nothing distinguishes a
  Create Aeronautics airship from a Create Offroad truck or any other
  Sable-based contraption. `AeronauticsWindForceApplier` applies wind
  force to **every** Sable sub-level uniformly and gates on **`sable`**
  being loaded, not `aeronautics` specifically — broadening "Create
  Aeronautics interaction" to "any Sable contraption interaction" in
  practice.
