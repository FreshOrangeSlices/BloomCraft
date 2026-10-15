package com.bloomcraft.bosses.reward;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.RankSettings;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Rng;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The independent reward rolls when a boss dies. */
public final class RewardManager {

	private final Bosses bosses;
	private final BossGearFactory gear;

	public RewardManager(Bosses bosses) {
		this.bosses = bosses;
		this.gear = new BossGearFactory(bosses);
	}

	public BossGearFactory gear() {
		return gear;
	}

	/**
	 * The main roll, with the safety nets: from the configured rank up a boss always drops something, and below it
	 * a player who comes up empty several times in a row is guaranteed a drop.
	 */
	public List<ItemStack> rollWithFloor(Boss boss, @Nullable ServerPlayer killer) {
		List<ItemStack> out = roll(boss, killer);
		BossSettings f = bosses.settings();
		boolean floor = f.rewardFloorFrom != null && boss.rank().atLeast(f.rewardFloorFrom);
		if (!out.isEmpty()) {
			if (killer != null) {
				Data.remove(killer, BossKeys.EMPTY_KILLS);
			}
			return out;
		}
		boolean pity = false;
		if (!floor && killer != null && f.pityAfter > 0) {
			CompoundTag pd = Data.of(killer);
			int dry = pd.getIntOr(BossKeys.EMPTY_KILLS, 0) + 1;
			pity = dry >= f.pityAfter;
			if (pity) {
				pd.remove(BossKeys.EMPTY_KILLS);
			} else {
				pd.putInt(BossKeys.EMPTY_KILLS, dry);
			}
		}
		if (floor || pity) {
			ItemStack guaranteed = Rng.chance(65) ? null : BossItems.randomRune(boss.rank());
			out.add(guaranteed != null ? guaranteed
				: gear.create(boss.rank(), boss.entity().getType(), sourceName(boss), null));
		}
		return out;
	}

	private static String sourceName(Boss boss) {
		String prefix = boss.rank().starText() + " ";
		return boss.plainName().startsWith(prefix) ? boss.plainName().substring(prefix.length()) : boss.plainName();
	}

	public List<ItemStack> roll(Boss boss, @Nullable ServerPlayer killer) {
		RankSettings.Rewards chances = bosses.settings().rank(boss.rank()).rewards();
		// Harder fights (Threat Scaling) pay better.
		double luck = bosses.relics().rewardMultiplier(killer)
			* (1.0 + BossManager.rewardThreat(boss.entity()) * bosses.settings().threatRewardBonus / 100.0);
		List<ItemStack> out = new ArrayList<>();
		if (Rng.chance(chances.bossGear() * luck)) {
			out.add(gear.create(boss.rank(), boss.entity().getType(), sourceName(boss), null));
		}
		if (Rng.chance(chances.empowerment() * luck)) {
			ItemStack rune = BossItems.randomRune(boss.rank());
			if (rune != null) {
				out.add(rune);
			}
		}
		// Any rank can drop any relic: higher ranks just drop more of them.
		if (Rng.chance(chances.relic() * luck)) {
			ItemStack relic = BossItems.randomRelic();
			if (relic != null) {
				out.add(relic);
			}
			for (double extra : chances.extraRelics()) {
				if (!Rng.chance(extra * luck)) {
					break;
				}
				ItemStack more = BossItems.randomRelic();
				if (more != null) {
					out.add(more);
				}
			}
		}
		if (bosses.settings().catalystEnabled && Rng.chance(chances.relicCatalyst() * luck)) {
			out.add(BossItems.catalyst());
		}
		return out;
	}
}
