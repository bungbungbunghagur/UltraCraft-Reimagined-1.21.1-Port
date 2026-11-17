package com.ultracraft;

import com.ultracraft.entity.UKEntities;
import com.ultracraft.item.UKItems;
import com.ultracraft.net.Net;
import com.ultracraft.weapon.WeaponServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** UltraCraft: V1 (ULTRAKILL) inside Minecraft NeoForge 1.21.1. */
@Mod(UltraCraft.MOD_ID)
public final class UltraCraft {
	public static final String MOD_ID = "ultracraft";
	public static final Logger LOG = LoggerFactory.getLogger("UltraCraft");

	public UltraCraft(IEventBus modBus, ModContainer modContainer) {
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, UkConfig.SPEC);
		UKItems.register(modBus);
		UKEntities.register(modBus);
		com.ultracraft.block.UKBlocks.register(modBus);
		modBus.addListener(Net::register);
		modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
			WeaponServer.init();
		}));
		if (FMLEnvironment.dist == Dist.CLIENT) com.ultracraft.client.UltraCraftClient.init(modBus, modContainer);
		LOG.info("UltraCraft loading");
	}
}
