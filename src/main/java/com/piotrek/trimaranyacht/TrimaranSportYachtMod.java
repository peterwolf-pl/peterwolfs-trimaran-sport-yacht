package com.piotrek.trimaranyacht;

import com.piotrek.trimaranyacht.entity.TrimaranSportYachtEntity;
import com.piotrek.trimaranyacht.item.TrimaranSportYachtItem;
import com.piotrek.trimaranyacht.network.TrimaranYachtInputPayload;
import com.piotrek.trimaranyacht.network.TrimaranYachtMooringPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrimaranSportYachtMod implements ModInitializer {
	public static final String MOD_ID = "peterwolfs_trimaran_sport_yacht";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	// Entity Registration
	public static final ResourceKey<EntityType<?>> TRIMARAN_SPORT_YACHT_KEY = ResourceKey.create(
		Registries.ENTITY_TYPE, id("trimaran_sport_yacht")
	);

	public static final EntityType<TrimaranSportYachtEntity> TRIMARAN_SPORT_YACHT = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		TRIMARAN_SPORT_YACHT_KEY,
		EntityType.Builder.of(TrimaranSportYachtEntity::new, MobCategory.MISC)
			.sized(11.25F, 2.5F)
			.eyeHeight(1.1F)
			.clientTrackingRange(16)
			.build(TRIMARAN_SPORT_YACHT_KEY)
	);

	// Item Registration
	public static final ResourceKey<Item> TRIMARAN_SPORT_YACHT_ITEM_KEY = ResourceKey.create(
		Registries.ITEM, id("trimaran_sport_yacht")
	);

	public static final TrimaranSportYachtItem TRIMARAN_SPORT_YACHT_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		TRIMARAN_SPORT_YACHT_ITEM_KEY,
		new TrimaranSportYachtItem(new Item.Properties().setId(TRIMARAN_SPORT_YACHT_ITEM_KEY).stacksTo(1))
	);

	// Creative Tab
	public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
		.title(Component.translatable("itemGroup.peterwolfs_trimaran_sport_yacht.group"))
		.icon(TRIMARAN_SPORT_YACHT_ITEM::getDefaultInstance)
		.displayItems((parameters, output) -> {
			output.accept(TRIMARAN_SPORT_YACHT_ITEM);
		})
		.build();

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Peterwolf's Trimaran Sport Yacht (Fabric 26.3)");

		if (FabricLoader.getInstance().isModLoaded("pw_wind_n_sails")) {
			LOGGER.info("Wind & Sails integration active.");
		} else {
			LOGGER.warn("pw_wind_n_sails not detected; true wind physics and anchor require Wind & Sails.");
		}

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("group"), TAB);

		// Add Trimaran to Wind & Sails creative tab if available
		ResourceKey<CreativeModeTab> WIND_N_SAILS_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("pw_wind_n_sails", "group")
		);
		CreativeModeTabEvents.modifyOutputEvent(WIND_N_SAILS_TAB).register(output -> {
			output.accept(TRIMARAN_SPORT_YACHT_ITEM);
		});

		// Network Registrations
		PayloadTypeRegistry.serverboundPlay().register(TrimaranYachtInputPayload.TYPE, TrimaranYachtInputPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TrimaranYachtMooringPayload.TYPE, TrimaranYachtMooringPayload.CODEC);

		// Client Input Processing
		ServerPlayNetworking.registerGlobalReceiver(TrimaranYachtInputPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (!Float.isFinite(payload.rudderInput()) || !Float.isFinite(payload.throttleOrSheetInput())) return;
				if (context.player().getVehicle() instanceof TrimaranSportYachtEntity yacht
					&& yacht.getId() == payload.yachtEntityId() && yacht.isHelmsman(context.player())) {
					yacht.setControlInput(payload.rudderInput(), payload.throttleOrSheetInput());
					if (payload.toggleEngine()) {
						yacht.toggleEngine(context.player());
					}
					if (payload.toggleSail()) {
						yacht.toggleSailOrCutEngine(context.player());
					}
					if (payload.targetHikeMode() == 0 || payload.targetHikeMode() == 1) {
						yacht.setHikeMode(payload.targetHikeMode(), context.player());
					}
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(TrimaranYachtMooringPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				// Never trust client-supplied entity IDs, cleat indices or fence positions.
				// Re-run the normal item/raycast validation on the server instead.
				TrimaranSportYachtEntity.handleAimedMooringClick(context.player());
			});
		});

		// Mooring & Helm Interaction Callbacks
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
			if (TrimaranSportYachtEntity.holdsAnchor(player) && com.piotrek.pwwindnsails.entity.AnchorEntity.findHeldBy(player) == null) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (TrimaranSportYachtEntity.handleAimedControl(player)) return net.minecraft.world.InteractionResult.SUCCESS;
			return TrimaranSportYachtEntity.handleAimedMooringClick(player) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
			if (TrimaranSportYachtEntity.holdsAnchor(player) && com.piotrek.pwwindnsails.entity.AnchorEntity.findHeldBy(player) == null) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (TrimaranSportYachtEntity.handleAimedControl(player)) return net.minecraft.world.InteractionResult.SUCCESS;
			return TrimaranSportYachtEntity.handleAimedMooringClick(player) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
			if (TrimaranSportYachtEntity.holdsAnchor(player) && com.piotrek.pwwindnsails.entity.AnchorEntity.findHeldBy(player) == null) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (entity instanceof TrimaranSportYachtEntity) return net.minecraft.world.InteractionResult.PASS;
			if (TrimaranSportYachtEntity.handleAimedControl(player)) return net.minecraft.world.InteractionResult.SUCCESS;

			if (TrimaranSportYachtEntity.handleEntityMooringClick(player, entity)) {
				return net.minecraft.world.InteractionResult.SUCCESS;
			}

			if (!(entity instanceof net.minecraft.world.entity.decoration.LeashFenceKnotEntity)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			return TrimaranSportYachtEntity.handleAimedMooringClick(player) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
		});
	}
}
