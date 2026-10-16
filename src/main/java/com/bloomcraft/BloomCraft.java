package com.bloomcraft;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Ticks;
import com.bloomcraft.core.WorldStore;
import com.bloomcraft.pets.Pets;
import com.bloomcraft.skills.Skills;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BloomCraft: bosses, relics, pets, quests and skills, as one Fabric mod for singleplayer and small worlds.
 *
 * <p>Each part lives in its own package with its own config file. This class starts them in order and drives the
 * server's lifecycle and tick for all of them.</p>
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
		Data.init();
		Skills.init();
		Bosses.init();
		Pets.init();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Ticks.server(server);
			WorldStore.load(server);
			Skills.get().starting(server);
			Bosses.get().starting(server);
			Pets.get().starting(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Pets.get().stopping(server);
			Bosses.get().stopping(server);
			Scheduler.clear();
			WorldStore.close();
			Ticks.server(null);
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			Pets.get().saving();
			WorldStore.save(false);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Ticks.tick();
			Scheduler.tick();
			Bosses.get().tick(server);
			Pets.get().tick(server);
		});
	}
}
