package com.bloomcraft.test;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.item.Statues;
import com.bloomcraft.bosses.nemesis.NemesisManager;
import com.bloomcraft.bosses.nemesis.NemesisRecord;
import com.bloomcraft.bosses.nemesis.Surrender;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The Nemesis system works in a running game. */
public class NemesisTest {

	@GameTest
	public void bossBecomesNemesis(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		Boss boss = Bosses.get().bosses().createBoss(zombie, BossRank.RED, "test", List.of(), false);
		NemesisManager nm = Bosses.get().nemesis();
		NemesisRecord r = nm.forceCreate(boss, player);
		Check.that(helper, r != null && boss.isNemesis(), "not a Nemesis");
		Check.that(helper, nm.forOwner(player.getUUID()).contains(r), "not listed for its owner");
		Check.that(helper, zombie.getCustomName() != null && zombie.getCustomName().getString().contains(r.name),
			"name: " + zombie.getCustomName());
		Check.that(helper, !zombie.shouldBeSaved(), "an active Nemesis must not be saved in the world");
		// its record survives a save and load
		NemesisRecord copy = NemesisRecord.load(r.id, r.save());
		Check.that(helper, copy != null && copy.name.equals(r.name) && copy.type == r.type && copy.level == r.level
			&& copy.surrender == r.surrender, "record round trip");
		// its Challenge points back at it
		ItemStack note = nm.createChallenge(r);
		Check.that(helper, r.id.equals(NemesisManager.challengeId(note)), "challenge id");
		Check.that(helper, BossItems.kind(note) == BossItems.Kind.CHALLENGE, "challenge kind");
		nm.clear(player.getUUID());
		Check.that(helper, nm.forOwner(player.getUUID()).isEmpty(), "cleared");
		helper.succeed();
	}

	@GameTest
	public void statuePlacesAndPicksUp(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		Boss boss = Bosses.get().bosses().createBoss(zombie, BossRank.PURPLE, "test", List.of(), false);
		NemesisRecord r = Bosses.get().nemesis().forceCreate(boss, player);
		Statues statues = Bosses.get().statues();
		ItemStack item = statues.create(boss, r, Bosses.get().nemesis().displayName(r));
		Check.that(helper, item != null && BossItems.kind(item) == BossItems.Kind.STATUE, "statue item");
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1, 3.5));
		Check.that(helper, statues.place(helper.getLevel(), item, at, 0), "statue not placed");
		Entity figure = statues.figureOn(helper.getLevel(), helper.absolutePos(new BlockPos(3, 0, 3)));
		Check.that(helper, figure != null && Statues.isStatue(figure), "no figure on the block");
		ItemStack back = statues.pickUp(figure);
		Check.that(helper, back != null && BossItems.kind(back) == BossItems.Kind.STATUE, "pick up");
		Check.that(helper, !figure.isAlive(), "figure still there");
		Bosses.get().nemesis().clear(player.getUUID());
		helper.succeed();
	}

	@GameTest
	public void surrenderTerms(GameTestHelper helper) {
		var sources = helper.getLevel().damageSources();
		Check.that(helper, Surrender.FALL.matches(sources.fall(), EntityTypes.ZOMBIE), "fall");
		Check.that(helper, Surrender.DROWNING.matches(sources.drown(), EntityTypes.ZOMBIE), "drown");
		Check.that(helper, !Surrender.FALL.matches(sources.drown(), EntityTypes.ZOMBIE), "drowning isn't a fall");
		for (int i = 0; i < 50; i++) {
			Surrender s = Surrender.pick(EntityTypes.ZOMBIE);
			Check.that(helper, s != Surrender.ZOMBIE && s != Surrender.DROWNED, "a zombie asked for its own kind: " + s);
		}
		helper.succeed();
	}
}
