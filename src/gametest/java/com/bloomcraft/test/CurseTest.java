package com.bloomcraft.test;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.bosses.relic.RelicEffect;
import com.bloomcraft.bosses.relic.RelicManager;
import com.bloomcraft.bosses.relic.effects.NatureCurses;
import com.bloomcraft.core.Spawns;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Curses work in a running game. */
public class CurseTest {

	private static final List<String> CURSES = List.of("dread", "butterfingers", "fowl-omen", "insomnia", "limelight",
		"gluttony", "recoil", "glass-bones", "blood-tithe", "terror", "echoes", "reduction", "matador", "mother-hen",
		"pariah", "herbivore", "withering-waters", "uninvited-guest", "dont-blink", "poltergeist", "restless-dead",
		"hiccups");

	@GameTest
	public void everyCurseIsThere(GameTestHelper helper) {
		RelicManager relics = Bosses.get().relics();
		for (String id : CURSES) {
			RelicEffect curse = relics.get(id);
			Check.that(helper, curse != null && curse.curse(), "missing curse " + id);
			Check.that(helper, relics.isEnabled(curse), "curse " + id + " is off");
			Check.that(helper, !curse.description().isBlank(), "curse " + id + " has no description");
		}
		helper.succeed();
	}

	/** Every curse's moment and once-a-second effect runs without errors, and cleans up after itself. */
	@GameTest(maxTicks = 200)
	public void cursesRun(GameTestHelper helper) {
		RelicManager relics = Bosses.get().relics();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(4, 1, 4));
		player.snapTo(at.x, at.y, at.z, 0, 0);
		player.getInventory().add(new ItemStack(Items.STONE_SWORD));
		RelicContext ctx = new RelicContext(player, RelicContext.Slot.ARMOR, relics);
		for (String id : CURSES) {
			RelicEffect curse = relics.get(id);
			if (RelicManager.hasTrigger(curse)) {
				curse.trigger(player, relics);
			}
			if (curse.passive()) {
				curse.onPassive(ctx);
			}
		}
		helper.runAfterDelay(60, () -> {
			for (String id : CURSES) {
				relics.get(id).onDeactivate(player);
			}
			helper.succeed();
		});
	}

	@GameTest
	public void herbivoreTurnsMeatIntoPlants(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 5));
		player.getInventory().add(new ItemStack(Items.ROTTEN_FLESH, 3));
		NatureCurses.convertMeat(player);
		Check.that(helper, player.getInventory().countItem(Items.COOKED_BEEF) == 0, "beef left");
		Check.that(helper, player.getInventory().countItem(Items.ROTTEN_FLESH) == 0, "flesh left");
		Check.that(helper, player.getInventory().countItem(Items.COAL) <= 5, "rotten flesh must not become coal");
		helper.succeed();
	}

	@GameTest
	public void effectMobsAreNeverSaved(GameTestHelper helper) {
		Chicken chick = Spawns.effectMob(helper.getLevel(), EntityTypes.CHICKEN, helper.absoluteVec(new Vec3(1, 1, 1)), 0,
			"test", c -> c.setBaby(true));
		Check.that(helper, chick != null, "not spawned");
		Check.that(helper, !chick.shouldBeSaved(), "would be saved");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Check.that(helper, chick.broadcastToPlayer(player), "should be visible");
		Spawns.onlyVisibleTo(chick, helper.makeMockServerPlayerInLevel());
		Check.that(helper, !chick.broadcastToPlayer(player), "should be hidden from other players");
		chick.discard();
		helper.succeed();
	}
}
