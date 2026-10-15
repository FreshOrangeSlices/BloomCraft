package com.bloomcraft.test;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.item.ModItems;
import com.bloomcraft.bosses.waystone.Waystone;
import com.bloomcraft.bosses.waystone.Waystones;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Waystones work in a running game. */
public class WaystoneTest {

	@GameTest
	public void waystoneJoinsTheNetwork(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos local = new BlockPos(2, 1, 2);
		helper.setBlock(local, ModItems.WAYSTONE_BLOCK);
		BlockPos pos = helper.absolutePos(local);
		Waystones ways = Bosses.get().waystones();
		ItemStack item = Waystones.item("Test Stone");
		Waystone w = ways.place(player, pos, item);
		Check.that(helper, w != null && "Test Stone".equals(w.name), "name: " + (w == null ? null : w.name));
		Check.that(helper, ways.at(helper.getLevel(), pos) == w, "not found at its block");
		Check.that(helper, ModItems.WAYSTONE_BLOCK.asItem() == ModItems.WAYSTONE, "the block doesn't know its item");
		ways.remove(w);
		Check.that(helper, ways.at(helper.getLevel(), pos) == null, "still there after removal");
		helper.succeed();
	}
}
