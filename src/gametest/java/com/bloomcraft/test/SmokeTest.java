package com.bloomcraft.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/** The mod loads in a running server. */
public class SmokeTest {

	@GameTest
	public void modLoads(GameTestHelper helper) {
		Check.that(helper, FabricLoader.getInstance().isModLoaded("bloomcraft"), "BloomCraft isn't loaded");
		helper.succeed();
	}
}
