package com.piotrek.trimaranyacht.network;

import com.piotrek.trimaranyacht.TrimaranSportYachtMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record TrimaranYachtMooringPayload(
	int yachtEntityId,
	int cleatIndex,
	Optional<BlockPos> fencePos
) implements CustomPacketPayload {
	public static final Type<TrimaranYachtMooringPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(TrimaranSportYachtMod.MOD_ID, "trimaran_mooring")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, TrimaranYachtMooringPayload> CODEC = new StreamCodec<>() {
		@Override
		public TrimaranYachtMooringPayload decode(RegistryFriendlyByteBuf buf) {
			int yachtId = buf.readVarInt();
			int cleat = buf.readVarInt();
			boolean hasPos = buf.readBoolean();
			Optional<BlockPos> pos = hasPos ? Optional.of(buf.readBlockPos()) : Optional.empty();
			return new TrimaranYachtMooringPayload(yachtId, cleat, pos);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, TrimaranYachtMooringPayload p) {
			buf.writeVarInt(p.yachtEntityId);
			buf.writeVarInt(p.cleatIndex);
			buf.writeBoolean(p.fencePos.isPresent());
			p.fencePos.ifPresent(buf::writeBlockPos);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
