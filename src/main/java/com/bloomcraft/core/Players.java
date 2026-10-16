package com.bloomcraft.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Small things done to players: giving items, using one up, how long they've been idle. */
public final class Players {

	private Players() {
	}

	/** Into the inventory, or dropped at their feet if it's full (or they're dead). */
	public static void give(ServerPlayer player, ItemStack item) {
		if (item.isEmpty()) {
			return;
		}
		if (!player.isAlive() || !player.getInventory().add(item)) {
			if (!item.isEmpty()) {
				player.spawnAtLocation(player.level(), item);
			}
		}
	}

	/** Uses one of the item in this hand (not in Creative). */
	public static void consume(ServerPlayer player, InteractionHand hand) {
		if (player.gameMode() == GameType.CREATIVE) {
			return;
		}
		ItemStack item = player.getItemInHand(hand);
		if (!item.isEmpty()) {
			item.shrink(1);
		}
	}

	public static boolean survivalLike(ServerPlayer player) {
		GameType mode = player.gameMode();
		return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
	}

	/** Seconds since the player last did something (moved, clicked, typed). */
	public static long idleSeconds(ServerPlayer player) {
		return Math.max(0, (Util.getMillis() - player.getLastActionTime()) / 1000);
	}
}
