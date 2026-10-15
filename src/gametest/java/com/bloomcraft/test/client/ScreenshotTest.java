package com.bloomcraft.test.client;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.item.ModItems;
import com.bloomcraft.bosses.relic.RelicEffect;
import com.bloomcraft.bosses.waystone.Waystones;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes screenshots in a real game (run by the Screenshots workflow): the inventory full of the mod's items, and a
 * waystone and a boss in the world. They show whether every texture and model loads.
 */
public class ScreenshotTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			context.waitTicks(40);
			game.getServer().runOnServer(ScreenshotTest::fillInventory);
			context.waitTicks(10);
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(20);
			context.takeScreenshot("bloomcraft_inventory");
			game.getServer().runOnServer(server -> fillInventoryPage(server, 1));
			context.waitTicks(10);
			context.takeScreenshot("bloomcraft_inventory_2");
			context.setScreen(() -> null);
			game.getServer().runOnServer(ScreenshotTest::buildScene);
			context.waitTicks(60);
			context.takeScreenshot("bloomcraft_world");
		}
	}

	private static List<ItemStack> showcase() {
		List<ItemStack> items = new ArrayList<>();
		for (BossRank rank : BossRank.values()) {
			items.add(BossItems.soul(rank, 1));
		}
		for (BossRank rank : BossRank.values()) {
			if (rank.natural()) {
				items.add(BossItems.totem(rank));
			}
		}
		items.add(BossItems.compass(1));
		items.add(BossItems.catalyst());
		items.add(Waystones.item(null));
		items.add(new ItemStack(ModItems.NEMESIS_STATUE));
		items.add(new ItemStack(ModItems.NEMESIS_CHALLENGE));
		for (var stat : Bosses.get().settings().empowermentStats) {
			items.add(BossItems.rune(BossRank.GOLD, stat, stat.roll(BossRank.GOLD, true)));
		}
		for (RelicEffect relic : Bosses.get().relics().enabledEffects(false)) {
			items.add(BossItems.relic(relic, null));
		}
		return items;
	}

	private static void fillInventory(MinecraftServer server) {
		fillInventoryPage(server, 0);
	}

	/** Page 0: the first 36 showcase items; page 1: the rest. */
	private static void fillInventoryPage(MinecraftServer server, int page) {
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		player.getInventory().clearContent();
		List<ItemStack> items = showcase();
		for (int i = 0; i < 36 && page * 36 + i < items.size(); i++) {
			player.getInventory().setItem(i, items.get(page * 36 + i));
		}
	}

	private static void buildScene(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		BlockPos base = player.blockPosition();
		BlockPos stone = base.offset(0, 0, 4);
		level.setBlockAndUpdate(stone.below(), Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(stone, ModItems.WAYSTONE_BLOCK.defaultBlockState());
		Bosses.get().waystones().place(player, stone, Waystones.item("Screenshot Stone"));
		var zombie = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (zombie != null) {
			zombie.snapTo(base.getX() + 3.5, base.getY(), base.getZ() + 6.5, 180, 0);
			zombie.setNoAi(true);
			level.addFreshEntity(zombie);
			Bosses.get().bosses().createBoss(zombie, BossRank.GOLD, "test", List.of(), false);
		}
		player.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 15);
		player.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 15);
	}
}
