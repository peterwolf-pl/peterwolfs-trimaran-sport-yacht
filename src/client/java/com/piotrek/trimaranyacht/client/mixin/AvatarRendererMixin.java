package com.piotrek.trimaranyacht.client.mixin;

import com.piotrek.trimaranyacht.entity.TrimaranSportYachtEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL")
	)
	private void trimaran$standAtHelm(
		Avatar entity,
		AvatarRenderState state,
		float partialTick,
		CallbackInfo ci
	) {
		if (entity.getVehicle() instanceof TrimaranSportYachtEntity yacht && yacht.isHelmsman(entity)) {
			state.isPassenger = false;
		}
	}
}
