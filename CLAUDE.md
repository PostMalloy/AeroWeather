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
- GeckoLib `4.9.2` — a **required** dependency, unlike Create/Sable (renders the wind vane, M10)
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

Root package `com.postmalloy.aeroweather`. All milestones through M9 are
complete, and M10 (the wind vane) is written (see Roadmap); this table is
the actual, current structure — keep it in sync as code changes.

```
AeroWeather.java                      main @Mod class: registries, config, subsystem bootstrap
AeroWeatherClient.java                 client @Mod entry: config screen registration

block/
  WindVaneBlock.java                  the zinc vane: BaseEntityBlock, ENTITYBLOCK_ANIMATED, no blockstate properties,
                                       client ticker only - a visual indicator with no redstone (see M10)
  BrassWindVaneBlock.java             extends WindVaneBlock: adds POWER + 8-way WIND_FROM blockstate, weak redstone
                                       from the upwind face(s), 20-tick server ticker
  WindVaneBlockEntity.java            GeoBlockEntity shared by both vanes; client-only transient heading, eased toward LocalWind
  WindVaneBlockItem.java              BlockItem + GeoItem for both vanes; same geo, texture named after its block

config/
  AeroWeatherCommonConfig.java        drift rate, gust chance/magnitude, rain/thunder boost, sync thresholds,
                                       aeronautics force, windmill and wind vane tuning
  AeroWeatherClientConfig.java        particle toggle, max count, spawn radius, outdoors-only flag, active-contraption gating

wind/
  WindDirection.java                  cardinal enum (StringRepresentable, backs WIND_FROM) + degree/vector helpers;
                                       travelVector(bearingDeg) -> Vec3, and its inverse bearingOf(Vec3)
  LocalWind.java                      wind as seen from one block's own grid, both sides, Sable-ship aware; shared
                                       by the wind vane and Create windmills (see M10)
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
                                       by the windmill mixin (no networking), keyed by bearing position but located at
                                       its world position (ship-aware); entries age out after 20 ticks
  particle/
    WindStreakParticle.java           TextureSheetParticle + nested Provider; shared by WIND_STREAK/WIND_GUST/WIND_LOOP
    AeroWeatherParticleProviders.java RegisterParticleProvidersEvent registration; also overrides vanilla's
                                       campfire smoke/cherry leaves providers (see below)
    WindParticleSpawner.java          ClientTickEvent.Post: spawns particles around player from ClientWindState
    AmbientWindDrift.java             pure function: wind-driven target velocity at a Y, for vanilla particle overrides below
    WindDriftingCampfireSmokeParticle.java  extends vanilla CampfireSmokeParticle, eases toward AmbientWindDrift's target
    WindDriftingCherryParticle.java   extends vanilla CherryParticle (cherry_leaves), same wind-easing treatment
  render/
    WindVaneGeoModel.java             DefaultedBlockGeoModel; turns the "vane" bone to the block entity's heading, and
                                       picks each vane's texture by its block id (one geo shared by both)
    WindVaneRenderer.java             GeoBlockRenderer; widens getRenderBoundingBox (Sable culls ships with it too)
    WindVaneItemRenderer.java         GeoItemRenderer; points the item's vane into the wind from the live render
                                       transform (hands, frames, ground); rests it in the GUI
    AeroWeatherBlockEntityRenderers.java  EntityRenderersEvent.RegisterRenderers registration

registry/
  AeroWeatherParticles.java           DeferredRegister<ParticleType<?>>: WIND_STREAK, WIND_GUST, WIND_LOOP
  AeroWeatherCommandArgumentTypes.java  DeferredRegister<ArgumentTypeInfo<?,?>>: registers DirectionArgument for command-tree sync
  AeroWeatherBlocks.java              DeferredRegister.Blocks: ZINC_WIND_VANE, BRASS_WIND_VANE (copper sounds, pickaxe, strength 3)
  AeroWeatherItems.java               DeferredRegister.Items: both vanes; brass in the Redstone Blocks tab, zinc in Functional Blocks
  AeroWeatherBlockEntityTypes.java    DeferredRegister<BlockEntityType<?>>: WIND_VANE, one type valid for both vane blocks

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
    AeronauticsWindForceApplier.java  one of only two classes allowed to reference Sable types (see isolation rule)
    AeronauticsIntegration.java       static get(); resolve() checks ModCompat.isLoaded("sable") BEFORE
                                       constructing AeronauticsWindForceApplier, catch(Throwable) -> Noop
    SubLevelFrames.java               Sable-free interface + lazy holder: frameAt(Level, BlockPos) -> the block's world
                                       position and a world-to-local direction rotation, or null off-ship / without Sable
    SableSubLevelFrames.java          the Sable-backed implementation; the second class allowed to reference Sable types
  create/
    CreateWindmillWind.java           windmill speed multiplier from LocalWind (so ship-aware); zero Create types,
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
ambience); a per-particle ±15° direction jitter (`directionJitterDegrees`) avoids
uniform drift.
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
Reversal itself is opt-in: `windmillReverseWhenBehind` defaults to `false`,
so by default wind from behind drives a windmill forwards, the same as
wind from the front.

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
onto the compile classpath). This works because the two Create methods it
shadows, `updateGeneratedRotation()` and `getGeneratedSpeed()`, have
Create-free `()V`/`()F` descriptors; and position/facing come from
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

## M10: Wind vane

Two placeable blocks sharing one model: a static base, and a
GeckoLib-animated vane that points **into** the wind (where it blows
FROM, like a real weathervane — matching how AeroWeather names wind
everywhere). The **zinc** vane (`zinc_wind_vane`) is purely a visual
indicator. The **brass** vane (`brass_wind_vane`) also emits an 8-way
weak redstone signal from its upwind face(s), scaled by local wind
strength. Both work on Sable ships too. Every GeckoLib fact below was verified against the real
`geckolib-neoforge-1.21.1-4.9.2` bytecode, and every Sable fact against
the pinned Sable 2.0.5 jar.

### Rendering (GeckoLib — a required dependency)

- `WindVaneBlock.getRenderShape` → `ENTITYBLOCK_ANIMATED`, so the chunk
  mesh draws nothing and `WindVaneRenderer` (a `GeoBlockRenderer`) draws
  the whole model. `WindVaneGeoModel` is a `DefaultedBlockGeoModel`, whose
  path convention (`geo/%s/%s.geo.json`, `textures/%s/%s.png`, subtype
  `block`) resolves its id, `wind_vane`, to `geo/block/wind_vane.geo.json`
  (textures are per block — see the next bullet). The geometry and the
  block entity type keep the plain `wind_vane` id because both vanes
  share them; only the zinc *block* is `zinc_wind_vane`.
- **One geo, a texture per block.** Both vanes share the block entity
  type, so a single renderer and model serve them. `WindVaneGeoModel`
  overrides the one-argument `getTextureResource(animatable)` to return
  `buildFormattedTexturePath(<block id>)` — `textures/block/zinc_wind_vane.png`
  or `textures/block/brass_wind_vane.png`. That's the right hook: every
  GeckoLib renderer's `getTextureLocation` calls the model's two-argument
  `getTextureResource(animatable, renderer)`, which delegates to it, and
  `DefaultedGeoModel` overrides only it. The item does the same through
  `withAltTexture(<block id>)`, which routes through that same
  `buildFormattedTexturePath`, so a block and its item can't disagree. A
  further variant needs only a texture named after its block id, plus the
  usual block/item/loot/lang entries.
- `models/item/brass_wind_vane.json` is just a `parent` of
  `aeroweather:item/zinc_wind_vane`. Vanilla decides `builtin/entity` from the
  root of the parent chain (`BlockModel.getRootModel()`), and display
  transforms and `gui_light` fall back to the parent per context — so
  both items share one set of hand and GUI transforms.
- **No animation file.** `GeoModel.getAnimationResource` is only reached
  through animation-controller lookups, and the vane registers no
  controllers. The `vane` bone is turned procedurally in
  `setCustomAnimations` via `getBone("vane")` → `GeoBone.setRotY`. That's
  in **radians** (GeckoLib's bone transform uses `Axis.YP.rotation`), and
  **negated**, because +Y turns counter-clockwise seen from above while
  compass bearings run clockwise.
- **Orientation needs no correction.** The block has no facing property, so
  `GeoBlockRenderer.getFacing` falls back to NORTH, and `rotateBlock(NORTH)`
  is a 0° turn (its switch map is SOUTH 180, WEST 90, NORTH 0, EAST 270).
  The model's origin is the block's bottom-centre (`translate(0.5, 0, 0.5)`).
  So the model renders exactly as authored: an arrowhead modeled pointing
  north (−Z) is heading 0. `MODEL_ARROW_BEARING_DEG` exists only for a
  future model authored another way. The shipped model was confirmed
  north-pointing from the `.bbmodel` itself: the fin's *east* face carries
  the arrow's texture region, and on an east face u runs south → north.
- Default render type is `entityCutoutNoCull`: cutout alpha, both sides of
  every face drawn, so a single zero-width plane works as a fin.
- `getRenderBoundingBox` is widened to ±1 block, since the default is the
  unit cube.
- The item is `WindVaneBlockItem implements GeoItem`, whose
  `createGeoRenderer` supplies a `WindVaneItemRenderer` (a
  `GeoItemRenderer`) over the same geo. No
  NeoForge client-extension registration is needed: GeckoLib's own
  `BlockEntityWithoutLevelRendererMixin` routes `builtin/entity` items.
  It's dedicated-server safe because GeckoLib only calls
  `createGeoRenderer` from a lazy provider gated on
  `GeckoLibServices.PLATFORM.isPhysicalClient()`.
- **The item's vane points into the wind** wherever it's drawn in the
  world. `WindVaneItemRenderer.renderRecursively` sets the `vane` bone's
  Y rotation just before GeckoLib draws it — on entry the pose stack is
  still the parent frame, since the default implementation pushes and
  then calls `RenderUtil.prepMatrixForBone` (turning about `Axis.YP`).
  It reads the model's X/Z axes off `poseStack.last().pose()` and solves
  `t = atan2(-X·w, -Z·w)` (w = the wind's FROM vector): a turn of `t`
  carries the model's −Z arrow to `-sin(t)·X - cos(t)·Z`, which lines up
  best with `w`. That's right at any tilt, so no special cases for each
  hand's display angle, left-hand mirroring, arm swing or head pitch; on
  a level model it reduces to the placed vane's `-bearing`. World and
  entity passes keep the camera rotation out of the pose stack (it
  travels in the frustum matrix handed to `LevelRenderer.renderLevel`),
  so those axes are already world space. **So is the first-person hand,
  despite appearances:** `GameRenderer.renderItemInHand` starts its fresh
  `PoseStack` with the *inverse* of that view rotation and puts the view
  rotation itself on the model-view stack, so the two cancel on screen
  but the pose stack stays world-aligned. (Its parameter is misleadingly
  named `projectionMatrix`; the caller passes the view rotation.) An
  earlier version also applied `Camera.rotation()` for `FIRST_PERSON_*`
  contexts, which rotated the held vane twice by the player's facing —
  it looked ~90° off. No context needs special-casing; `GUI`/`NONE` rest
  at 0.
- **Why the bone must be set on every draw, GUI included:** GeckoLib bakes
  each geo file once (`GeckoLibCache`), so the item and every placed vane
  share the same `GeoBone` objects. A renderer that leaves the bone alone
  draws whatever angle the last placed vane set — which is what made the
  held item and inventory icon point in arbitrary directions.
- Each blockstate points at a **particle-only** model (just
  `textures.particle`, set to that vane's own texture), used for break
  and landing particles. The single `""` variant matches every state:
  the zinc vane's only one, and every `POWER`/`WIND_FROM` combination of
  the brass vane's.
- Model workflow: Blockbench's "GeckoLib Models & Animations" plugin, a
  GeckoLib Animated Model of type Block/Item, exported over `geo/block/wind_vane.geo.json` and
  `textures/block/zinc_wind_vane.png` (the brass texture is a separate file). The `.bbmodel` lives in `img/`, and `build.gradle`'s
  `**/*.bbmodel` exclude keeps it out of the jar.

### Redstone (brass only)

Only `BrassWindVaneBlock` emits. It's a subclass rather than a flag on
`WindVaneBlock` because `Block`'s constructor builds the state definition
(`createBlockStateDefinition`) before any subclass field is assigned, so
a flag couldn't choose which properties exist. The zinc vane has no
properties, isn't a signal source, and runs no server ticker.

The signal mirrors vanilla's daylight detector: weak power only (no
`getDirectSignal`), the value held in the blockstate, a server ticker
re-evaluating every 20 ticks, and `setBlock(UPDATE_ALL)` only on change —
so observers pulse whenever the reading changes.

- `power = clamp(round(adjustedStrength / windVaneFullSignalStrength * 15), 0, 15)`.
  The config default is 50, matching `windmillFullSpeedStrength`, so the
  wind that runs a windmill at full speed also maxes a vane.
- `WIND_FROM = WindDirection.nearest(bearing)` (±22.5° sectors). A face
  emits iff `angularDifference(windFrom, faceBearing) <= 45`, which is one
  face for a cardinal wind and two for a diagonal. `WIND_FROM` is held while
  power is 0, so a calm vane doesn't churn state or pulse observers.
- **Direction convention (easy to get backwards):** in
  `getSignal(state, level, pos, direction)`, `direction` points from the
  querying neighbour *toward* this block — `SignalGetter.hasNeighborSignal`
  asks its north neighbour with `Direction.NORTH`. So the emitting face is
  `direction.getOpposite()`.
- `rotate`/`mirror` turn `WIND_FROM` with the block.
- Collision is the model's 3px base slab; the outline covers the whole
  cell, so aiming at the vane itself picks the block.

### Heading animation

A client-only block entity ticker eases a transient (non-NBT) heading
toward `LocalWind`'s bearing:
`heading += wrapDegrees(target − heading) × 0.15 × clamp(strength / windVaneFullSignalStrength, 0, 1)`.
In calm air the vane holds still; in strong wind it tracks quickly. The
first reading snaps rather than eases, so a vane doesn't sweep round every
time its chunk loads. Rendering interpolates with
`Mth.rotLerp(partialTick, previous, current)`.

### On Sable ships (true wind)

All ship handling lives in `wind/LocalWind`, which both the vane and
Create windmills use:

- `SubLevelFrames.get().frameAt(level, pos)` finds a block's ship, if any.
  `SableSubLevelFrames` implements it as `SubLevelContainer.getContainer(level)`
  → `inBounds(pos)` → `getPlot(ChunkPos)` → `getSubLevel()` →
  `logicalPose()`, null-checked at each step. `getContainer` is null for
  levels Sable doesn't manage, and `inBounds` is a plain range check that
  rejects every ordinary world block before any lookup. It uses only Sable's
  common API, so the same code serves both sides.
- **World block:** exactly the pre-M10 windmill computation (height scaling
  at `pos.getY()`, raw bearing), so world windmills are bit-identical to 1.1.0.
- **Ship block:** blocks on a ship live in real level chunks at far-away
  plot coordinates, so strength is height-scaled at the block's *world* Y
  (`pose.transformPosition(Vec3.atCenterOf(pos))`). The FROM vector is
  rotated into the ship's frame (`transformNormalInverse`) and turned back
  into a bearing with `WindDirection.bearingOf`. Strength is scaled by the
  wind's share in the ship's horizontal plane — full on a level ship,
  fading to 0 as it pitches on end.
- **Ship rendering and culling need nothing from us.** Sable's
  `sublevel_render.block_entity_render.LevelRendererMixin` wraps every
  block entity renderer in `ClientSubLevel.renderPose()`, and
  `block_entity_visible.LevelRendererMixin` culls by transforming *our*
  `getRenderBoundingBox` by the ship's pose.
- **True wind, deliberately:** the ship's own motion is ignored, matching
  the contraption force and windmills. If apparent wind is ever wanted,
  `ServerSubLevel.latestLinearVelocity`/`latestAngularVelocity` are set in
  `SubLevelPhysicsSystem.updatePose` as a per-tick value ×20, i.e. blocks
  per second.
- Windmills: `ClientActiveWindmills` keys each sighting by the bearing's
  own `BlockPos` (a stable identity) but locates it at
  `SubLevelFrames.worldPositionOf(...)`, so a ship-mounted windmill counts
  for the particle gate where it actually is.

**Recipes** (`data/aeroweather/recipe/`): shaped `zzz` / ` z ` / `ccc` —
zinc nuggets over andesite casing for the zinc vane, brass nuggets over
brass casing for the brass one (the brass recipe files under the
redstone recipe-book tab, zinc under misc). Every ingredient is a Create
item, so each recipe carries `"neoforge:conditions": [{"type":
"neoforge:mod_loaded", "modid": "create"}]` (key and codec field verified
against NeoForge 21.1.248's `ConditionalOps`/`ModLoadedCondition`).
Without Create they're skipped cleanly — no recipe parse error in the
log — and the vanes are creative-only. There are no recipe-book unlock
advancements, so the recipe book lists them only once crafted (JEI/EMI
show them regardless).

**Still the user's call:** the material (copper sounds, pickaxe,
strength 3 as a placeholder for both; map colour orange for zinc, gold
for brass).

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

- **Isolation rule**: only two classes in the mod may reference Sable or
  Sable-Companion types — `AeronauticsWindForceApplier` (M7) and
  `SableSubLevelFrames` (M10). Each is only ever constructed by a
  Sable-free holder that checks `ModCompat.isLoaded("sable")` first
  (`AeronauticsIntegration.get()` and `SubLevelFrames.Resolved`
  respectively), so the JVM never attempts to resolve a Sable class
  reference without a `ModList` check first. Both holders deliberately
  return an *interface* type (`WindForceApplier` / `SubLevelFrames`): the
  bytecode verifier doesn't load a class just to check it's assignable to
  an interface, so the Sable-typed implementation stays unloaded until
  it's actually constructed. Don't narrow those return types to the
  concrete class.
  Event registration inside `AeronauticsWindForceApplier` is deliberately
  manual/instance-based (`NeoForge.EVENT_BUS.addListener(...)` /
  `modEventBus.addListener(...)`), **not** this codebase's usual
  `@EventBusSubscriber` + static `@SubscribeEvent` pattern — that pattern
  gets scanned/registered by NeoForge at mod-init regardless of intent,
  which would force-classload Sable's event types even when Sable isn't
  installed. Do not "clean this up" to the usual annotation pattern.
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
- **GeckoLib is the exception to all of the above (M10).** It's a
  *required* dependency (`type="required"` in `neoforge.mods.toml`,
  `implementation` in `build.gradle`), so the wind vane's classes
  reference it directly with no gate. Keep GeckoLib usage confined to
  the vane's `block/` and `client/render/` classes anyway.

## Out of scope (for now)

Not building yet, don't add speculative abstractions for these — YAGNI,
but don't actively make future extension harder either:

- New weather pattern types (tornadoes, custom storms, etc.)
- New blocks/items beyond the two wind vanes (anemometers, handheld wind
  meters, etc.)
- Anything beyond wind simulation + its Create/Create Aeronautics
  interaction (windmill response and contraption force are in scope;
  other Create kinetic generators are not)

## Roadmap / milestones

M0–M9 are complete — repo scaffolding, wind simulation core, networking
sync, the `/aeroweather` command, particles, config, the Create
Aeronautics/Sable research spike (M6), the force-application
implementation (M7, see "M7: Aeronautics wind force" above), live
integration testing against a real assembled airship (M8, which found
and fixed two real bugs: the `onSubLevelAdded` lift-ratio timing issue
and the contraption-diagram disconnect from an unregistered
`ForceGroup` — both documented above/in "External references"), and the
Create windmill wind response (M9, confirmed working in a live client
after one fix: the mixin had shadowed an inherited field — see the M9
section's `@Shadow` note). A **without-Create** launch (the mixin
config-plugin gate) still hasn't been explicitly tested. See `git log`
for detailed history of each milestone.

**M10 (the wind vane, see the section above — including Sable ship
support, and the shared `LocalWind` that also made windmills ship-aware)
has been reported working in a live client. The later zinc/brass split
(redstone moved to the brass vane, per-block textures) is compile-verified
only.** Verified
statically: every GeckoLib and Sable API used, against the real jars; the
built jar ships the geo model, texture, blockstate, block and item
models, loot table and pickaxe tag, and no `.bbmodel`; the generated
`mods.toml` declares GeckoLib required. Not specifically confirmed yet: the item's vane
pointing into the wind in first person (third person is confirmed), in item
frames and on the ground, the vane's heading in-game (world and on a ship), redstone
faces on a turned ship, the item's hand and inventory transforms, and
windmills on ships.

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
