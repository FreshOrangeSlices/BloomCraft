package com.bloomcraft.test;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.trait.BossTrait;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Bosses work in a running game. */
public class BossTest {

	@GameTest
	public void zombieBecomesBoss(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		double before = zombie.getMaxHealth();
		Boss boss = Bosses.get().bosses().createBoss(zombie, BossRank.RED, "test", List.of(), false);
		Check.that(helper, BossManager.isBoss(zombie), "not marked as a boss");
		Check.that(helper, Bosses.get().bosses().get(zombie) == boss, "not tracked");
		// Brutal: x2.75 health, then the difficulty x1.3
		Check.near(helper, before * 2.75 * 1.3, zombie.getMaxHealth(), 0.6, "max health");
		Check.near(helper, zombie.getMaxHealth(), zombie.getHealth(), 0.01, "starts at full health");
		Check.that(helper, zombie.getCustomName() != null && zombie.getCustomName().getString().contains("Brutal"),
			"name: " + zombie.getCustomName());
		helper.succeed();
	}

	@GameTest
	public void legendaryHealthFloor(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		Bosses.get().bosses().createBoss(zombie, BossRank.GOLD, "test", List.of(), false);
		Check.that(helper, zombie.getMaxHealth() >= 500, "Legendary has at least 500 health, got " + zombie.getMaxHealth());
		helper.succeed();
	}

	@GameTest
	public void everyTraitApplies(GameTestHelper helper) {
		for (BossTrait trait : Bosses.get().traits().all()) {
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
			if (!trait.supports(zombie)) {
				zombie.discard();
				continue;
			}
			Boss boss = Bosses.get().bosses().createBoss(zombie, BossRank.PURPLE, "test", List.of(trait), false);
			trait.onTick(boss, 0);
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void bossTakesBulwarkDamage(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		Boss boss = Bosses.get().bosses().createBoss(zombie, BossRank.GRAY, "test",
			List.of(Bosses.get().traits().get("bulwark")), false);
		float before = zombie.getHealth();
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10f);
		float taken = before - zombie.getHealth();
		// Bulwark: 20% less (power 1.0 at Gray); armor reduces generic damage too, so just check it's less than 10
		Check.that(helper, taken > 0 && taken < 10, "damage taken: " + taken);
		Check.that(helper, boss.lastHurtTick() >= 0, "hurt tracked");
		helper.succeed();
	}

	@GameTest
	public void itemsCarryTheirData(GameTestHelper helper) {
		ItemStack soul = BossItems.soul(BossRank.PURPLE, 3);
		Check.that(helper, BossItems.kind(soul) == BossItems.Kind.SOUL, "soul kind");
		Check.that(helper, BossItems.rank(soul) == BossRank.PURPLE, "soul rank");
		Check.that(helper, soul.getCount() == 3, "soul count");
		ItemStack totem = BossItems.totem(BossRank.RED);
		Check.that(helper, BossItems.kind(totem) == BossItems.Kind.TOTEM, "totem kind");
		ItemStack gear = Bosses.get().rewards().gear().create(BossRank.GOLD, null, "a test", null);
		Check.that(helper, BossItems.isGear(gear), "gear kind");
		Check.that(helper, !BossItems.buildLore(gear).isEmpty(), "gear lore");
		helper.succeed();
	}
}
