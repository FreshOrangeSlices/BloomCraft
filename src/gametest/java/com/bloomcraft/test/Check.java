package com.bloomcraft.test;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/** Assertions for game tests. */
public final class Check {

	private Check() {
	}

	public static void that(GameTestHelper helper, boolean condition, String message) {
		if (!condition) {
			throw new GameTestAssertException(Component.literal(message), (int) helper.getTick());
		}
	}

	public static void near(GameTestHelper helper, double expected, double actual, double tolerance, String what) {
		that(helper, Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + ", got " + actual);
	}
}
