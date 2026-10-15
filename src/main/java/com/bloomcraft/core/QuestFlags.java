package com.bloomcraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Counters on the player that the Quest Book reads ("boss_kill", "relic_bound"...). Each mark counts up by one;
 * the Quest Book checks them against its quests.
 */
public final class QuestFlags {

	private static final String ROOT = "quest_flags";

	private QuestFlags() {
	}

	public static void mark(Player player, String flag) {
		CompoundTag flags = Data.of(player).getCompoundOrEmpty(ROOT);
		flags.putInt(flag, flags.getIntOr(flag, 0) + 1);
		Data.of(player).put(ROOT, flags);
	}

	/** Marks it only if it was never marked (counts something that's true now, not an event). */
	public static void markOnce(Player player, String flag) {
		if (count(player, flag) == 0) {
			mark(player, flag);
		}
	}

	public static int count(Player player, String flag) {
		CompoundTag data = Data.peek(player);
		return data == null ? 0 : data.getCompoundOrEmpty(ROOT).getIntOr(flag, 0);
	}
}
