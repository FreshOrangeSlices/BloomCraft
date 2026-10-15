package com.bloomcraft.bosses.feature;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.core.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * The Hunter's Compass: while held, its needle points at the nearest boss in range (your own Nemesis first), and
 * the action bar shows the distance and which way to turn. A heartbeat plays near dangerous bosses.
 */
public final class CompassManager {

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private final Bosses bosses;

	public CompassManager(Bosses bosses) {
		this.bosses = bosses;
	}

	/** About once a second while the compass is in a hand. */
	public void update(ServerPlayer player, ItemStack compass) {
		BossSettings f = bosses.settings();
		if (!f.compassEnabled) {
			compass.remove(DataComponents.LODESTONE_TRACKER);
			return;
		}
		BossSettings.CompassTier tier = f.compassTier(BossItems.compassTier(compass));
		Vec3 here = player.position();
		Boss best = null;
		double bestDist = Double.MAX_VALUE;
		Boss ownNemesis = null;
		double ownDist = Double.MAX_VALUE;
		UUID me = player.getUUID();
		for (Boss boss : bosses.bosses().active()) {
			if (boss.entity().level() != player.level()) {
				continue;
			}
			double d = boss.entity().position().distanceTo(here);
			if (d > tier.range()) {
				continue;
			}
			if (d < bestDist) {
				bestDist = d;
				best = boss;
			}
			if (boss.isNemesis() && me.equals(bosses.nemesis().owner(boss)) && d < ownDist) {
				ownDist = d;
				ownNemesis = boss;
			}
		}
		if (ownNemesis != null) {
			best = ownNemesis; // your own Nemesis always takes priority
			bestDist = ownDist;
		}
		if (best == null) {
			Fx.actionBar(player, Component.literal("No boss within " + Math.round(tier.range()) + " blocks")
				.withStyle(ChatFormatting.DARK_GRAY));
			compass.remove(DataComponents.LODESTONE_TRACKER);
			return;
		}
		Vec3 at = best.entity().position();
		compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
			Optional.of(GlobalPos.of(player.level().dimension(), BlockPos.containing(at))), false));

		boolean revealed = best.isNemesis() || bestDist <= tier.revealDistance();
		Component name = revealed ? best.name() : Component.literal("☠ Unknown Boss").withStyle(ChatFormatting.GRAY);
		double dy = at.y - here.y;
		String vertical = dy > 6 ? " ▲" : dy < -6 ? " ▼" : "";
		Fx.actionBar(player, Component.empty().append(name)
			.append(Component.literal("   " + Math.round(bestDist) + "m ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(arrow(player, at) + vertical).withStyle(ChatFormatting.YELLOW)));

		boolean dangerous = best.isNemesis() || best.rank().atLeast(BossRank.PURPLE);
		if (dangerous && bestDist <= 48) {
			float closeness = (float) (1.0 - bestDist / 48.0);
			Fx.playTo(player, "entity.warden.heartbeat", 0.4f + closeness * 0.8f, 0.8f + closeness * 0.5f);
		}
	}

	/** Arrow relative to where the player is looking: ↑ ahead, → to the right, ↓ behind... */
	private static String arrow(ServerPlayer from, Vec3 to) {
		double dx = to.x - from.getX();
		double dz = to.z - from.getZ();
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double diff = targetYaw - from.getYRot();
		diff = ((diff % 360) + 540) % 360 - 180;
		int index = (int) Math.round(diff / 45.0);
		return ARROWS[((index % 8) + 8) % 8];
	}
}
