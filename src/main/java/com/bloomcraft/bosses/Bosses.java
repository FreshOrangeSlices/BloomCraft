package com.bloomcraft.bosses;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.command.BossesCommand;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.feature.CompassManager;
import com.bloomcraft.bosses.feature.EscalationManager;
import com.bloomcraft.bosses.feature.FeatureManager;
import com.bloomcraft.bosses.item.ModItems;
import com.bloomcraft.bosses.nemesis.NemesisManager;
import com.bloomcraft.bosses.relic.CurseEvents;
import com.bloomcraft.bosses.relic.RelicManager;
import com.bloomcraft.bosses.reward.RewardManager;
import com.bloomcraft.bosses.trait.BossTrait;
import com.bloomcraft.bosses.trait.TraitManager;
import com.bloomcraft.core.ConfigFiles;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Ticks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** The bosses part of the mod: settings, managers and the game events they listen to. */
public final class Bosses {

	private static @Nullable Bosses instance;

	private @Nullable BossSettings settings;
	private final TraitManager traits = new TraitManager();
	private final BossManager bosses = new BossManager(this);
	private final BossBars bossBars = new BossBars(this);
	private final Presentation presentation = new Presentation(this);
	private final RewardManager rewards = new RewardManager(this);
	private final RelicManager relics;
	private final NemesisManager nemesis = new NemesisManager(this);
	private final EscalationManager escalation = new EscalationManager(this);
	private final FeatureManager features = new FeatureManager(this);
	private final CompassManager compass = new CompassManager(this);

	private Bosses() {
		instance = this;
		relics = new RelicManager(this);
	}

	public static Bosses get() {
		return instance;
	}

	public static void init() {
		Bosses mod = new Bosses();
		Data.init();
		mod.load();
		ModItems.init();
		mod.registerEvents();
	}

	/** (Re)reads config/bloomcraft/bosses.yml. */
	public void load() {
		settings = new BossSettings(ConfigFiles.load("bosses.yml"));
		traits.load(settings.traits);
		relics.load(settings.relics);
	}

	public @Nullable BossSettings settings() {
		return settings;
	}

	public TraitManager traits() {
		return traits;
	}

	public BossManager bosses() {
		return bosses;
	}

	public BossBars bossBars() {
		return bossBars;
	}

	public Presentation presentation() {
		return presentation;
	}

	public RewardManager rewards() {
		return rewards;
	}

	public RelicManager relics() {
		return relics;
	}

	public NemesisManager nemesis() {
		return nemesis;
	}

	public EscalationManager escalation() {
		return escalation;
	}

	public FeatureManager features() {
		return features;
	}

	public CompassManager compass() {
		return compass;
	}

	public void later(int ticks, Runnable task) {
		Scheduler.later(ticks, task);
	}

	// =====================================================================
	// Events
	// =====================================================================

	private void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Ticks.server(server);
			load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			bosses.shutdown();
			relics.shutdown(server);
			Scheduler.clear();
			Ticks.server(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(this::tick);

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (settings == null) {
				return;
			}
			bosses.onLoad(entity);
			if (entity instanceof Projectile projectile) {
				onLaunch(projectile);
			} else if (entity instanceof ItemEntity item) {
				features.onItemLoad(item);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> bosses.onUnload(entity));
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, from, to) -> {
			if (entity instanceof ServerPlayer player && settings != null) {
				relics.refresh(player);
			}
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (settings == null) {
				return true;
			}
			Boss boss = bosses.get(entity);
			if (boss != null) {
				for (BossTrait trait : boss.traits()) {
					if (trait.onLethalDamage(boss)) {
						return false;
					}
				}
				return true;
			}
			return !(entity instanceof ServerPlayer player && relics.tryResurrect(player));
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (settings == null) {
				return;
			}
			Boss boss = bosses.get(entity);
			if (boss != null) {
				bosses.handleDeath(boss);
			}
			if (entity instanceof ServerPlayer player) {
				onPlayerDeath(player, source.getEntity());
			} else if (source.getEntity() instanceof ServerPlayer killer && entity != killer) {
				relics.onKill(killer, entity, boss != null);
			}
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, keepEquipment) -> {
			Boss boss = bosses.get(previous);
			if (boss == null) {
				return;
			}
			if (boss.isNemesis() || !(converted instanceof Enemy)) {
				converted.setCustomName(null); // a cured zombie villager isn't a boss
				converted.setCustomNameVisible(false);
				return;
			}
			bosses.transfer(boss, converted);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> relics.refresh(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			relics.forget(handler.getPlayer());
			escalation.forget(handler.getPlayer().getUUID());
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			BossesCommand.register(dispatcher, this));
		CurseEvents.register(this);
	}

	private void tick(MinecraftServer server) {
		Ticks.tick();
		Scheduler.tick();
		if (settings == null) {
			return;
		}
		int now = Ticks.now();
		if (now % 10 == 0) {
			bosses.tick();
		}
		if (now % 20 == 0) {
			relics.tickPassives(server);
		}
	}

	private void onLaunch(Projectile projectile) {
		if (!(projectile.getOwner() instanceof LivingEntity shooter) || projectile.tickCount > 1) {
			return;
		}
		Boss boss = bosses.get(shooter);
		if (boss == null) {
			return;
		}
		for (BossTrait trait : boss.traits()) {
			trait.onProjectileLaunch(boss, projectile);
		}
		for (BossTrait trait : boss.traits()) {
			trait.afterProjectileLaunch(boss, projectile);
		}
	}

	/** A boss (or its minion) killed a player: it remembers, may become a Nemesis, and leaves after two. */
	private void onPlayerDeath(ServerPlayer player, @Nullable Entity killer) {
		Boss boss = bosses.get(killer);
		if (boss == null && killer != null && BossManager.isMinion(killer)) {
			String owner = Data.str(killer, BossKeys.MINION);
			try {
				boss = owner == null ? null : bosses.get(UUID.fromString(owner));
			} catch (IllegalArgumentException ignored) {
				boss = null;
			}
		}
		if (boss != null) {
			bosses.onKilledPlayer(boss, player);
		}
		BloomCraft.LOG.debug("{} died", player.getName().getString());
	}
}
