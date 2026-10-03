package com.piotrek.trimaranyacht.network;

import com.piotrek.trimaranyacht.TrimaranSportYachtMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TrimaranYachtInputPayload(
	int yachtEntityId,
	float rudderInput,
	float throttleOrSheetInput,
	boolean toggleEngine,
	boolean toggleSail,
	int targetHikeMode
) implements CustomPacketPayload {
	public static final Type<TrimaranYachtInputPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(TrimaranSportYachtMod.MOD_ID, "trimaran_input")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, TrimaranYachtInputPayload> CODEC = new StreamCodec<>() {
		@Override
		public TrimaranYachtInputPayload decode(RegistryFriendlyByteBuf buf) {
			return new TrimaranYachtInputPayload(
				buf.readVarInt(),
				buf.readFloat(),
				buf.readFloat(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readVarInt()
			);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, TrimaranYachtInputPayload p) {
			buf.writeVarInt(p.yachtEntityId);
			buf.writeFloat(p.rudderInput);
			buf.writeFloat(p.throttleOrSheetInput);
			buf.writeBoolean(p.toggleEngine);
			buf.writeBoolean(p.toggleSail);
			buf.writeVarInt(p.targetHikeMode);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
