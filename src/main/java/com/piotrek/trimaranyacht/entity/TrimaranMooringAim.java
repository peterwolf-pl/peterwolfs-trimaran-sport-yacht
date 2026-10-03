package com.piotrek.trimaranyacht.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * High-precision mooring raycast aim for Trimaran Sport Yacht cleats and dock fence posts.
 * Replicates the exact feel and behavior of pw_wind_n_sails mooring system.
 */
public final class TrimaranMooringAim {
	public static final double REACH = 14.0;

	// Precision cleat bounds matching the visible 2x1x6 horn (model units: 2w, 1h, 6l = 0.125w, 0.0625h, 0.375l)
	public static final double CLEAT_HALF_X = 0.16;
	public static final double CLEAT_HALF_Z = 0.28;
	public static final double CLEAT_HALF_Y = 0.18;

	// Vanilla fence post column bounds
	public static final double POST_MIN = 0.375;
	public static final double POST_MAX = 0.625;
	private static final double CLICK_PAD = 0.08;

	private TrimaranMooringAim() {}

	public record Target(boolean cleat, int yachtId, int cleatIndex, BlockPos fence, double distance) {}

	public static Target find(Player player, Level level, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		if (look.lengthSqr() < 1.0E-8) {
			return null;
		}
		look = look.normalize();
		Vec3 end = eye.add(look.scale(REACH));

		BlockHitResult blockHit = level.clip(new ClipContext(
			eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double occlude = REACH;
		BlockPos occludePos = null;
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			occlude = eye.distanceTo(blockHit.getLocation());
			occludePos = blockHit.getBlockPos();
		}

		Target best = null;
		AABB yachtSearch = player.getBoundingBox().inflate(REACH + 4.0);
		for (TrimaranSportYachtEntity yacht : level.getEntitiesOfClass(TrimaranSportYachtEntity.class, yachtSearch)) {
			Vec3 yachtPos = yacht.getPosition(partialTick);
			float yaw = Mth.rotLerp(partialTick, yacht.yRotO, yacht.getYRot());
			for (int i = 0; i < TrimaranSportYachtEntity.CLEAT_COUNT; i++) {
				double dist = cleatDistance(eye, look, yachtPos, yaw, yacht.getCleatLocalOffset(i), CLICK_PAD);
				if (dist > REACH || dist > occlude + 0.10) {
					continue;
				}
				if (best == null || dist < best.distance) {
					best = new Target(true, yacht.getId(), i, BlockPos.ZERO, dist);
				}
			}
		}

		// Fence post check
		if (occludePos != null && level.getBlockState(occludePos).is(BlockTags.FENCES)) {
			double fenceDist = postDistance(eye, look, occludePos);
			if (fenceDist <= REACH && (best == null || fenceDist < best.distance)) {
				best = new Target(false, 0, -1, occludePos.immutable(), fenceDist);
			}
		}

		return best;
	}

	public static AABB cleatLocalBox(Vec3 offset, double pad) {
		return new AABB(
			offset.x - CLEAT_HALF_X - pad,
			offset.y - CLEAT_HALF_Y - pad,
			offset.z - CLEAT_HALF_Z - pad,
			offset.x + CLEAT_HALF_X + pad,
			offset.y + CLEAT_HALF_Y + pad,
			offset.z + CLEAT_HALF_Z + pad
		);
	}

	public static AABB postColumn(BlockPos pos) {
		return new AABB(
			pos.getX() + POST_MIN, pos.getY(), pos.getZ() + POST_MIN,
			pos.getX() + POST_MAX, pos.getY() + 1.0, pos.getZ() + POST_MAX
		);
	}

	private static double cleatDistance(Vec3 eye, Vec3 look, Vec3 boatPos, float yaw, Vec3 offset, double pad) {
		float yawRad = yaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);

		Vec3 relEye = eye.subtract(boatPos);
		double localEyeX = relEye.x * rightX + relEye.z * rightZ;
		double localEyeY = relEye.y;
		double localEyeZ = relEye.x * fwdX + relEye.z * fwdZ;

		double localLookX = look.x * rightX + look.z * rightZ;
		double localLookY = look.y;
		double localLookZ = look.x * fwdX + look.z * fwdZ;

		Vec3 localStart = new Vec3(localEyeX, localEyeY, localEyeZ);
		Vec3 localEnd = localStart.add(localLookX * REACH, localLookY * REACH, localLookZ * REACH);

		AABB box = cleatLocalBox(offset, pad);
		Optional<Vec3> hit = box.clip(localStart, localEnd);
		return hit.map(localStart::distanceTo).orElse(Double.POSITIVE_INFINITY);
	}

	private static double postDistance(Vec3 eye, Vec3 look, BlockPos pos) {
		Vec3 end = eye.add(look.scale(REACH));
		Optional<Vec3> hit = postColumn(pos).clip(eye, end);
		return hit.map(eye::distanceTo).orElse(Double.POSITIVE_INFINITY);
	}
}
