package com.piotrek.trimaranyacht.item;

import com.piotrek.trimaranyacht.TrimaranSportYachtMod;
import com.piotrek.trimaranyacht.entity.TrimaranSportYachtEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;

public class TrimaranSportYachtItem extends Item {

	public TrimaranSportYachtItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);

		if (hit.getType() == HitResult.Type.MISS) {
			return InteractionResult.PASS;
		}

		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos hitPos = BlockPos.containing(hit.getLocation());

			// Verify water at hit position or adjacent
			if (!level.getFluidState(hitPos).is(FluidTags.WATER)) {
				hitPos = hitPos.above();
			}

			if (!level.getFluidState(hitPos).is(FluidTags.WATER)) {
				return InteractionResult.PASS;
			}

			// Two water blocks are required at the launch point.
			boolean hasDepth = level.getFluidState(hitPos).is(FluidTags.WATER)
				&& level.getFluidState(hitPos.below(1)).is(FluidTags.WATER);

			if (!hasDepth) {
				if (!level.isClientSide()) {
					TrimaranSportYachtEntity.notifyPlayer(player,
						Component.translatable("message.peterwolfs_trimaran_sport_yacht.water_too_shallow"));
					level.playSound(null, hitPos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, 0.8F);
				}
				return InteractionResult.FAIL;
			}

			if (level instanceof ServerLevel serverLevel) {
				TrimaranSportYachtEntity yacht = TrimaranSportYachtMod.TRIMARAN_SPORT_YACHT.create(
					serverLevel,
					EntitySpawnReason.SPAWN_ITEM_USE
				);

				if (yacht == null) return InteractionResult.FAIL;
				{
					yacht.setPos(hit.getLocation().x, hitPos.getY() + 0.58, hit.getLocation().z);
					yacht.setYRot(player.getYRot());
					yacht.updateBoundingBox();
					if (yacht.isHullColliding(yacht.getX(), yacht.getY(), yacht.getZ(), yacht.getYRot())) return InteractionResult.FAIL;
					if (!serverLevel.addFreshEntity(yacht)) return InteractionResult.FAIL;
					level.gameEvent(player, GameEvent.ENTITY_PLACE, hitPos);
					level.playSound(null, yacht.getX(), yacht.getY(), yacht.getZ(),
						SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.2F, 0.9F);
				}

				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}

			player.awardStat(Stats.ITEM_USED.get(this));
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}
}
