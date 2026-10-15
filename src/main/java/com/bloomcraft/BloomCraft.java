package com.bloomcraft;

import com.bloomcraft.bosses.Bosses;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BloomCraft: bosses, relics, pets, quests and skills, as one Fabric mod for singleplayer and small worlds.
 */
public final class BloomCraft implements ModInitializer {

	public static final String MOD_ID = "bloomcraft";
	public static final Logger LOG = LoggerFactory.getLogger("BloomCraft");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
		LOG.info("BloomCraft {} loading", version);
		Bosses.init();
	}
}
