package com.bloomcraft.bosses;

import com.bloomcraft.bosses.config.BossSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Boss bars, shown only to players actually fighting the boss (engaged within the combat timeout and within
 * view distance). Hidden when combat ends, removed when the boss dies, despawns or unloads.
 */
public final class BossBars {

	private final Bosses bosses;

	public BossBars(Bosses bosses) {
		this.bosses = bosses;
	}

	public void create(Boss boss) {
		if (boss.bar() != null) {
			return;
		}
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), title(boss, boss.health()), boss.rank().barColor(),
			boss.rank().barOverlay());
		bar.setProgress(progress(boss, boss.health()));
		boss.bar(bar);
	}

	public void updateHealth(Boss boss) {
		updateHealth(boss, boss.health());
	}

	public void updateHealth(Boss boss, double health) {
		ServerBossEvent bar = boss.bar();
		if (bar == null) {
			return;
		}
		bar.setProgress(progress(boss, health));
		if (bosses.settings().showHealthNumbers) {
			bar.setName(title(boss, health));
		}
	}

	/** Re-renders the title after the boss was renamed. */
	public void retitle(Boss boss) {
		ServerBossEvent bar = boss.bar();
		if (bar != null) {
			bar.setName(title(boss, boss.health()));
		}
	}

	/** Called by the ticker: shows the bar to engaged nearby players and hides it from everyone else. */
	public void refreshViewers(Boss boss) {
		ServerBossEvent bar = boss.bar();
		if (bar == null) {
			return;
		}
		BossSettings s = bosses.settings();
		double maxDistSq = s.bossbarViewDistance * s.bossbarViewDistance;
		PlayerList players = boss.level().getServer().getPlayerList();
		Set<UUID> shouldSee = new HashSet<>();
		for (UUID id : boss.engaged().keySet()) {
			ServerPlayer p = players.getPlayer(id);
			if (p != null && p.level() == boss.entity().level() && p.distanceToSqr(boss.entity()) <= maxDistSq) {
				shouldSee.add(id);
			}
		}
		Iterator<UUID> it = boss.barViewers().iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			if (!shouldSee.contains(id)) {
				ServerPlayer p = players.getPlayer(id);
				if (p != null) {
					bar.removePlayer(p);
				}
				it.remove();
			}
		}
		for (UUID id : shouldSee) {
			if (boss.barViewers().add(id)) {
				ServerPlayer p = players.getPlayer(id);
				if (p != null) {
					bar.addPlayer(p);
				}
			}
		}
		updateHealth(boss);
	}

	public void remove(Boss boss) {
		ServerBossEvent bar = boss.bar();
		if (bar == null) {
			return;
		}
		bar.removeAllPlayers();
		boss.barViewers().clear();
		boss.bar(null);
	}

	private Component title(Boss boss, double health) {
		if (!bosses.settings().showHealthNumbers) {
			return boss.name();
		}
		long shown = (long) Math.ceil(Math.max(0, health));
		long max = Math.round(boss.maxHealth());
		return Component.empty().append(boss.name())
			.append(Component.literal("  ❤ " + shown + "/" + max).withStyle(ChatFormatting.GRAY));
	}

	private static float progress(Boss boss, double health) {
		return (float) Math.max(0.0, Math.min(1.0, health / boss.maxHealth()));
	}
}
