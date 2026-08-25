package com.postmalloy.aeroweather.network.payload;

import java.util.List;

import com.postmalloy.aeroweather.AeroWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server-&gt;client sync of world-space positions where wind force is
 * currently being applied to a Sable sub-level, for one dimension — used
 * client-side to gate wind particles to near active contraptions (see
 * {@code AeroWeatherClientConfig.RESTRICT_TO_ACTIVE_CONTRAPTIONS}).
 * Broadcast on a fixed low cadence from {@code AeronauticsWindForceApplier}
 * (a plain {@code LevelTickEvent.Post} listener, decoupled from Sable's own
 * much-more-frequent physics-substep tick) — always sent, even when the
 * list is empty, so stale positions get cleared correctly once nothing is
 * active. Deliberately not folded into {@link ClientboundWindSyncPayload}:
 * that's wind *state*, this is contraption *positions*, a different
 * concern with a different (and much higher) natural update cadence.
 */
public record ClientboundActiveContraptionsPayload(ResourceLocation dimension, List<Vec3> positions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundActiveContraptionsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AeroWeather.MODID, "active_contraptions"));

    private static final StreamCodec<ByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);

    public static final StreamCodec<ByteBuf, ClientboundActiveContraptionsPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ClientboundActiveContraptionsPayload::dimension,
            VEC3_STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundActiveContraptionsPayload::positions,
            ClientboundActiveContraptionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
