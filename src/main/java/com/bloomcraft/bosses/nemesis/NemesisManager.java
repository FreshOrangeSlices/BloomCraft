package com.bloomcraft.bosses.nemesis;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Nemesis system. (Being ported: until then no boss becomes a Nemesis, and these hooks do nothing.)
 */
public final class NemesisManager {

	private final Bosses bosses;

	public NemesisManager(Bosses bosses) {
		this.bosses = bosses;
	}

	public int scarLevel(Boss boss) {
		return 0;
	}

	public @Nullable UUID owner(Boss boss) {
		return null;
	}

	/** True if the Nemesis withdrew this tick. */
	public boolean tickActive(Boss boss) {
		return false;
	}

	/** True if the boss left because the player fled. */
	public boolean onPlayerFled(Boss boss, UUID player) {
		return false;
	}

	/** True if this boss is (or just became) a Nemesis and handles its own leaving. */
	public boolean onKilledPlayer(Boss boss, ServerPlayer victim, int kills) {
		return false;
	}

	public boolean canReattach(String nemesisId, LivingEntity entity) {
		return false;
	}

	public void reattach(Boss boss, String nemesisId) {
	}

	public void onUnload(Boss boss) {
	}

	public BossRank soulRank(Boss boss) {
		return boss.rank();
	}

	public void onSlain(Boss boss, @Nullable ServerPlayer killer, List<ItemStack> rewards) {
	}
}
