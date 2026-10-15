package com.bloomcraft.bosses.feature;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.EmpowermentStat;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.core.Rng;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;

/**
 * The Loot and Fortune runes: more drops from mobs (Loot, on the weapon) and from ores (Fortune, on the tool).
 *
 * <p>Only loot-table drops are multiplied, so nothing a mob was holding or carrying (it may have picked up a player's
 * stack) and no placeable block (a silk-touched ore) can be multiplied over and over.</p>
 */
public final class LootBonus {

	private LootBonus() {
	}

	public static void register(Bosses mod) {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (mod.settings() == null || drops.isEmpty()) {
				return;
			}
			BlockState block = context.getOptional(LootContextParams.BLOCK_STATE);
			if (block != null) {
				ores(mod, context, block, drops);
			} else if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof LivingEntity dead
				&& context.getOptional(LootContextParams.DAMAGE_SOURCE) != null) {
				mobs(mod, context, dead, drops);
			}
		});
	}

	private static void mobs(Bosses mod, LootContext context, LivingEntity dead, List<ItemStack> drops) {
		Player killer = context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER);
		// Mobs that carry an inventory are skipped entirely.
		if (killer == null || dead instanceof Player || dead instanceof InventoryCarrier) {
			return;
		}
		double bonus = BossItems.effectBonus(killer.getMainHandItem(), EmpowermentStat.LOOT);
		if (bonus > 0) {
			scaleAll(drops, Math.min(mod.settings().maxLootMultiplier, 1.0 + bonus), false);
		}
	}

	private static void ores(Bosses mod, LootContext context, BlockState block, List<ItemStack> drops) {
		String path = BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath();
		if (!(path.endsWith("_ore") || block.is(Blocks.AMETHYST_CLUSTER))) {
			return;
		}
		ItemInstance tool = context.getOptional(LootContextParams.TOOL);
		Entity breaker = context.getOptional(LootContextParams.THIS_ENTITY);
		if (!(tool instanceof ItemStack stack) || !(breaker instanceof Player)) {
			return;
		}
		double bonus = BossItems.effectBonus(stack, EmpowermentStat.FORTUNE);
		if (bonus > 0) {
			scaleAll(drops, Math.min(mod.settings().maxFortuneMultiplier, 1.0 + bonus), true);
		}
	}

	private static void scaleAll(List<ItemStack> drops, double multiplier, boolean skipBlocks) {
		List<ItemStack> extra = new ArrayList<>();
		for (int i = 0; i < drops.size(); i++) {
			ItemStack drop = drops.get(i);
			if (drop.isEmpty() || drop.getMaxStackSize() <= 1 || BossItems.kind(drop) != null
				|| (skipBlocks && drop.getItem() instanceof BlockItem)) {
				continue;
			}
			drops.set(i, scaled(drop, multiplier, extra));
		}
		drops.addAll(extra);
	}

	/** The stack multiplied (the fraction rolled as a chance); anything above a full stack goes into {@code extra}. */
	private static ItemStack scaled(ItemStack drop, double multiplier, List<ItemStack> extra) {
		double exact = drop.getCount() * multiplier;
		int amount = (int) Math.floor(exact);
		if (Rng.r().nextDouble() < exact - amount) {
			amount++;
		}
		int max = drop.getMaxStackSize();
		while (amount > max) {
			extra.add(drop.copyWithCount(max));
			amount -= max;
		}
		return drop.copyWithCount(Math.max(1, amount));
	}
}
