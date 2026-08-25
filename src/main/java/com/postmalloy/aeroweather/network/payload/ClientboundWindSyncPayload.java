package com.postmalloy.aeroweather.network.payload;

import com.postmalloy.aeroweather.AeroWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-&gt;client sync of the effective wind for one dimension. See
 * CLAUDE.md's "Wind system design" for when this is sent (join/dimension
 * change/respawn, command overrides, and periodic delta-threshold
 * updates — never every tick).
 */
public record ClientboundWindSyncPayload(ResourceLocation dimension, float directionDeg, float strength, boolean gusting) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundWindSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AeroWeather.MODID, "wind_sync"));

    public static final StreamCodec<ByteBuf, ClientboundWindSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ClientboundWindSyncPayload::dimension,
            ByteBufCodecs.FLOAT, ClientboundWindSyncPayload::directionDeg,
            ByteBufCodecs.FLOAT, ClientboundWindSyncPayload::strength,
            ByteBufCodecs.BOOL, ClientboundWindSyncPayload::gusting,
            ClientboundWindSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
