package com.bloomcraft.bosses.feature;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.Messages;
import com.bloomcraft.core.Clock;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kill enough bosses within one Minecraft day and worse ones come looking for you. */
public final class EscalationManager {

	private final Bosses bosses;
	private final Map<UUID, Deque<Long>> kills = new HashMap<>();
	private final Map<UUID, Long> cooldownUntil = new HashMap<>();

	public EscalationManager(Bosses bosses) {
		this.bosses = bosses;
	}

	private BossSettings f() {
		return bosses.settings();
	}

	public int progress(ServerPlayer player) {
		Deque<Long> d = kills.get(player.getUUID());
		if (d == null) {
			return 0;
		}
		prune(d, Clock.now());
		return d.size();
	}

	private void prune(Deque<Long> d, long now) {
		long window = Math.round(f().escalationWindowDays * Clock.DAY);
		while (!d.isEmpty() && (now - d.peekFirst() > window || d.peekFirst() > now)) {
			d.removeFirst();
		}
	}

	public void recordKill(ServerPlayer player) {
		BossSettings f = f();
		if (!f.escalationEnabled) {
			return;
		}
		long now = Clock.now();
		UUID id = player.getUUID();
		Deque<Long> d = kills.computeIfAbsent(id, k -> new ArrayDeque<>());
		d.addLast(now);
		prune(d, now);
		if (d.size() < f.escalationKills || now < cooldownUntil.getOrDefault(id, Long.MIN_VALUE)) {
			return;
		}
		d.clear();
		cooldownUntil.put(id, now + Math.round(f.escalationWindowDays * Clock.DAY));
		trigger(player);
	}

	/** Warns the player, then (after the configured delay) brings in the escalation bosses. */
	public void trigger(ServerPlayer player) {
		Messages m = bosses.settings().messages;
		Fx.title(player, m.get("escalation-title"), m.get("escalation-subtitle"), 6, 60, 16);
		if (bosses.settings().bossChat) {
			player.sendSystemMessage(m.prefixed("escalation-subtitle", Map.of()));
		}
		Fx.playTo(player, "event.raid.horn", 1.0f, 0.8f);
		UUID id = player.getUUID();
		MinecraftServer server = player.level().getServer();
		Scheduler.later((int) Math.max(1, Math.round(f().escalationDelaySeconds * 20)), () -> arrive(server, id));
	}

	private void arrive(MinecraftServer server, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()
			|| !bosses.settings().worldAllowed(player.level())) {
			return;
		}
		BossSettings f = f();
		ServerLevel level = player.level();
		Fx.playTo(player, "entity.wither.spawn", 0.6f, 1.2f);
		for (int i = 0; i < f.escalationBosses; i++) {
			Vec3 spot = SafeSpots.around(level, player.position(), 12, 20, 16);
			if (spot == null) {
				continue;
			}
			EntityType<?> type = BossMobs.pick(level);
			BossRank rank = BossMobs.rollRank(f.escalationRanks, BossRank.PURPLE);
			Fx.particle(level, ParticleTypes.REVERSE_PORTAL, spot.add(0, 1, 0), 60, 0.6, 0.2);
			Fx.play(level, spot, "entity.evoker.prepare_summon", 1.0f, 0.7f);
			Boss boss = bosses.bosses().summon(type, level, spot, rank, null, true, BossMobs.categoryId(type));
			if (boss != null) {
				BossManager.markCreated(boss, "escalation");
				bosses.bosses().engage(boss, player);
				if (boss.entity() instanceof Mob mob) {
					mob.setTarget(player);
				}
			}
		}
	}

	public void forget(UUID id) {
		kills.remove(id);
	}
}
