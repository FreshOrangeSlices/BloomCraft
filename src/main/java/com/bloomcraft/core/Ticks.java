package com.bloomcraft.core;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/** The running server and a tick counter (counted by the mod, so it only ever goes up). */
public final class Ticks {

	private static int now;
	private static @Nullable MinecraftServer server;

	private Ticks() {
	}

	public static int now() {
		return now;
	}

	public static void tick() {
		now++;
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}

	public static void server(@Nullable MinecraftServer s) {
		server = s;
	}
}
