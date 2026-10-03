package com.piotrek.trimaranyacht.client;

import com.piotrek.trimaranyacht.entity.TrimaranSportYachtEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class TrimaranSportYachtHudOverlay implements HudElement {
	public static final TrimaranSportYachtHudOverlay INSTANCE = new TrimaranSportYachtHudOverlay();

	private TrimaranSportYachtHudOverlay() {}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !(client.player.getVehicle() instanceof TrimaranSportYachtEntity yacht)) {
			return;
		}

		Font font = client.font;
		int width = extractor.guiWidth();
		int height = extractor.guiHeight();

		Vec3 motion = yacht.getDeltaMovement();
		double horizSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		double speedKnots = horizSpeed * 38.87;

		int hudX = width / 2;
		int hudY = height - 70;

		// 1. Grounded Warning Banner
		if (yacht.isGrounded()) {
			boolean flash = (client.level != null && (client.level.getGameTime() % 16 < 8));
			int alertColor = flash ? 0xFFFF3333 : 0xFFFFAA00;
			extractor.fill(hudX - 170, hudY - 18, hudX + 170, hudY - 4, 0xD0000000);
			extractor.outline(hudX - 170, hudY - 18, 340, 14, alertColor);
			extractor.centeredText(font,
				Component.translatable("message.peterwolfs_trimaran_sport_yacht.grounded_hud"),
				hudX, hudY - 15, alertColor);
		}

		// 2. Main Telemetry Box
		extractor.fill(hudX - 170, hudY, hudX + 170, hudY + 28, 0x95000000);
		extractor.outline(hudX - 170, hudY, 340, 28, 0x40FFFFFF);

		// Drive status line
		Component driveMode;
		int driveColor;
		if (yacht.isEngineActive()) {
			int throttlePct = Math.round(yacht.getEngineThrottle() * 100.0F);
			String pctStr = (throttlePct > 0 ? "+" : "") + throttlePct + "%";
			if (throttlePct > 2) {
				driveMode = Component.translatable("hud.peterwolfs_trimaran_sport_yacht.motor_forward", pctStr);
				driveColor = 0xFF55FF55;
			} else if (throttlePct < -2) {
				driveMode = Component.translatable("hud.peterwolfs_trimaran_sport_yacht.motor_reverse", pctStr);
				driveColor = 0xFFFFAA00;
			} else {
				driveMode = Component.translatable("hud.peterwolfs_trimaran_sport_yacht.motor_neutral");
				driveColor = 0xFFFFFF55;
			}
		} else if (!yacht.isSailFurled()) {
			int sheetPct = Math.round(yacht.getMainsheet() * 100.0F);
			driveMode = Component.translatable("hud.peterwolfs_trimaran_sport_yacht.sails_hoisted", sheetPct + "%");
			driveColor = 0xFF55FFFF;
		} else {
			driveMode = Component.translatable("hud.peterwolfs_trimaran_sport_yacht.sails_furled_engine_off");
			driveColor = 0xFFFFFF55;
		}

		float rudder = yacht.getRudderAngle();
		Component rudderComp = Math.abs(rudder) < 1.0F
			? Component.translatable("hud.peterwolfs_trimaran_sport_yacht.helm_center")
			: (rudder < 0
				? Component.translatable("hud.peterwolfs_trimaran_sport_yacht.helm_port", String.format("%.0f", -rudder))
				: Component.translatable("hud.peterwolfs_trimaran_sport_yacht.helm_starboard", String.format("%.0f", rudder)));

		Component hikeComp = (yacht.getHikeMode() == 1)
			? Component.translatable("hud.peterwolfs_trimaran_sport_yacht.hike_windward")
			: Component.translatable("hud.peterwolfs_trimaran_sport_yacht.hike_center");

		Component depthComp = yacht.isGrounded()
			? Component.translatable("hud.peterwolfs_trimaran_sport_yacht.depth_grounded")
			: Component.translatable("hud.peterwolfs_trimaran_sport_yacht.depth_safe");

		Component line1 = Component.empty()
			.append(driveMode)
			.append(Component.literal("  |  "))
			.append(rudderComp);

		String speedStr = String.format("%.1f", speedKnots);
		Component line2 = Component.translatable("hud.peterwolfs_trimaran_sport_yacht.line2",
			speedStr, hikeComp, depthComp);

		extractor.centeredText(font, line1, hudX, hudY + 4, driveColor);
		extractor.centeredText(font, line2, hudX, hudY + 16, 0xFFDDDDDD);
	}
}
