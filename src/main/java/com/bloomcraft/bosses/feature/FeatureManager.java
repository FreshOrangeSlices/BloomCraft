package com.bloomcraft.bosses.feature;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.Presentation;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.QuestFlags;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Scheduler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Boss Totems (the summoning ritual) and Boss Souls (offering one to a boss makes it rise). */
public final class FeatureManager {

	private final Bosses bosses;
	private final Set<UUID> rituals = new HashSet<>();

	public FeatureManager(Bosses bosses) {
		this.bosses = bosses;
	}

	// =====================================================================
	// Boss Totem
	// =====================================================================

	public void useTotem(ServerPlayer player, ItemStack item) {
		BossSettings f = bosses.settings();
		if (!f.totemEnabled || !f.worldAllowed(player.level())) {
			player.sendSystemMessage(f.messages.prefixed("totem-blocked", java.util.Map.of()));
			return;
		}
		if (!rituals.add(player.getUUID())) {
			return; // a ritual is already running
		}
		BossRank stored = BossItems.rank(item);
		BossRank rank = stored != null ? stored : BossRank.GREEN;
		player.sendSystemMessage(f.messages.prefixed("totem-ritual", java.util.Map.of()));
		Fx.play(player, "item.totem.use", 0.7f, 0.6f);
		Fx.play(player, "ambient.cave", 1.0f, 0.8f);
		UUID id = player.getUUID();
		MinecraftServer server = player.level().getServer();
		ServerLevel startLevel = player.level();
		int[] step = {0};
		Scheduler.repeat(1, 10, () -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || !p.isAlive() || p.level() != startLevel) {
				rituals.remove(id);
				return false;
			}
			step[0]++;
			ServerLevel level = p.level();
			Vec3 c = p.position().add(0, 1, 0);
			double radius = 2.5 - step[0] * 0.35;
			DustParticleOptions dust = new DustParticleOptions(rank.rgb(), 1.4f);
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12 + step[0] * 0.4;
				level.sendParticles(dust, c.x + Math.cos(a) * radius, c.y - 0.8 + step[0] * 0.25, c.z + Math.sin(a) * radius,
					1, 0, 0, 0, 0);
			}
			Fx.particle(level, ParticleTypes.SOUL, c, 4, 0.6, 0.02);
			Fx.play(level, c, "block.note_block.basedrum", 0.8f, 0.5f + step[0] * 0.1f);
			if (step[0] < 6) {
				return true;
			}
			rituals.remove(id);
			// the totem is only used up once the ritual completes
			if (!p.isCreative() && !consumeTotem(p, rank)) {
				p.sendSystemMessage(bosses.settings().messages.prefixed("totem-blocked", java.util.Map.of()));
				return false;
			}
			summonFromTotem(p, rank);
			return false;
		});
	}

	private void summonFromTotem(ServerPlayer player, BossRank rank) {
		ServerLevel level = player.level();
		Vec3 spot = SafeSpots.around(level, player.position(), 5, 9, 16);
		if (spot == null) {
			spot = SafeSpots.around(level, player.position(), 2, 5, 16);
		}
		if (spot == null) {
			player.sendSystemMessage(bosses.settings().messages.prefixed("totem-blocked", java.util.Map.of()));
			give(player, BossItems.totem(rank));
			return;
		}
		EntityType<?> type = BossMobs.pick(level);
		Presentation.lightningEffect(level, spot);
		Fx.dust(level, spot.add(0, 1, 0), rank.rgb(), 2.0f, 40, 0.8);
		Fx.particle(level, ParticleTypes.LARGE_SMOKE, spot.add(0, 1, 0), 30, 0.5, 0.05);
		Boss boss = bosses.bosses().summon(type, level, spot, rank, null, true, BossMobs.categoryId(type));
		if (boss != null) {
			BossManager.markCreated(boss, "totem");
			QuestFlags.mark(player, "totem_used");
			bosses.bosses().engage(boss, player);
			if (boss.entity() instanceof Mob mob) {
				mob.setTarget(player);
			}
		}
	}

	/** Removes one totem of this rank from anywhere in the inventory. False if the player has none any more. */
	private static boolean consumeTotem(ServerPlayer player, BossRank rank) {
		Inventory inv = player.getInventory();
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack stack = inv.getItem(slot);
			if (BossItems.kind(stack) == BossItems.Kind.TOTEM && BossItems.rank(stack) == rank) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	// =====================================================================
	// Boss Souls
	// =====================================================================

	/** Offers a Soul to a boss: it rises by the soul's strength (Gray souls only sometimes work). True if used up. */
	public boolean offerSoul(@Nullable ServerPlayer player, ItemStack soul, Boss boss) {
		BossSettings f = bosses.settings();
		if (!f.promotionEnabled) {
			return false;
		}
		if (boss.isNemesis() || boss.rank() == BossRank.ASCENDANT) {
			if (player != null) {
				player.sendSystemMessage(f.messages.prefixed("promote-blocked", java.util.Map.of()));
			}
			return false;
		}
		BossRank soulRank = BossItems.rank(soul);
		if (soulRank == null) {
			soulRank = BossRank.GRAY;
		}
		int[] range = f.promotionSteps.getOrDefault(soulRank, new int[]{1, 1});
		int steps = Rng.between(range[0], range[1]);
		if (soulRank == BossRank.GRAY) {
			steps = Rng.chance(f.grayPromotionChance) ? Math.max(1, steps) : 0;
		}
		if (steps <= 0) {
			Fx.particle(boss.level(), ParticleTypes.ASH, Fx.center(boss.entity()), 30, 0.5, 0.02);
			Fx.play(boss.entity(), "block.sand.break", 1.0f, 0.6f);
			if (player != null) {
				player.sendSystemMessage(f.messages.prefixed("promote-fail", java.util.Map.of()));
			}
			return true;
		}
		bosses.bosses().promote(boss, steps, player);
		return true;
	}

	/** A thrown (dropped) soul flies a little further, and if it touches a boss it's offered to it. */
	public void onItemLoad(ItemEntity thrown) {
		if (BossItems.kind(thrown.getItem()) != BossItems.Kind.SOUL || !bosses.settings().promotionEnabled
			|| !(thrown.getOwner() instanceof ServerPlayer player) || thrown.getAge() > 2) {
			return;
		}
		Vec3 look = player.getLookAngle().scale(0.9).add(0, 0.15, 0);
		thrown.setDeltaMovement(look);
		UUID thrower = player.getUUID();
		MinecraftServer server = player.level().getServer();
		int[] ticks = {0};
		Scheduler.repeat(2, 2, () -> {
			if (!thrown.isAlive() || (ticks[0] += 2) > 60) {
				return false;
			}
			ServerLevel level = (ServerLevel) thrown.level();
			Fx.particle(level, ParticleTypes.END_ROD, thrown.position(), 1, 0.05, 0);
			for (Entity near : level.getEntities(thrown, thrown.getBoundingBox().inflate(1.2, 1.5, 1.2))) {
				Boss boss = bosses.bosses().get(near);
				if (boss == null) {
					continue;
				}
				ItemStack stack = thrown.getItem();
				if (offerSoul(server.getPlayerList().getPlayer(thrower), stack, boss)) {
					// one soul is used; the rest of a thrown stack stays on the ground
					if (stack.getCount() > 1) {
						ItemStack rest = stack.copy();
						rest.shrink(1);
						thrown.setItem(rest);
					} else {
						thrown.discard();
					}
				}
				return false;
			}
			return true;
		});
	}

	/**
	 * Quest Book flags for what a player carries (Boss Gear, a found Relic, a Soul), checked every few seconds, so
	 * items from a pickup, a chest or a command all count.
	 */
	public void questFlags(ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack item = inv.getItem(i);
			BossItems.Kind kind = BossItems.kind(item);
			if (kind == null) {
				continue;
			}
			switch (kind) {
				case GEAR -> QuestFlags.markOnce(player, "boss_gear");
				case RELIC -> {
					if (!BossItems.data(item).getBooleanOr("starter", false)) {
						QuestFlags.markOnce(player, "relic_found");
					}
				}
				case SOUL -> QuestFlags.markOnce(player, "soul_found");
				default -> {
				}
			}
		}
	}

	private static void give(ServerPlayer player, ItemStack item) {
		if (!player.getInventory().add(item)) {
			player.spawnAtLocation(player.level(), item);
		}
	}
}
