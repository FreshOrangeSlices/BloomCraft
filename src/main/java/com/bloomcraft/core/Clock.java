package com.bloomcraft.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * "Minecraft days" for timers (Nemesis returns, Escalation windows, statue dates).
 *
 * <p>It counts the overworld's game time, which always moves forward (even with the daylight cycle off), plus any
 * time skipped by sleeping or /time add, so a night slept through counts like a night waited out.</p>
 */
public final class Clock {

	public static final long DAY = 24000L;

	private static long offset;
	private static long lastGame = -1;
	private static long lastDay = -1;

	private Clock() {
	}

	public static void load(long savedOffset) {
		offset = Math.max(0, savedOffset);
		lastGame = -1;
		lastDay = -1;
	}

	public static long offset() {
		return offset;
	}

	public static long now() {
		MinecraftServer server = Ticks.server();
		if (server == null) {
			return 0;
		}
		ServerLevel main = server.overworld();
		long game = main.getGameTime();
		long day = main.getOverworldClockTime();
		if (lastGame >= 0) {
			long gameDelta = game - lastGame;
			long dayDelta = day - lastDay;
			if (gameDelta >= 0 && dayDelta > gameDelta + 20) {
				offset += dayDelta - gameDelta; // the night was skipped
			}
		}
		lastGame = game;
		lastDay = day;
		return game + offset;
	}

	/** Day number for display (day 1 = the first day). */
	public static long day() {
		return now() / DAY + 1;
	}
}
