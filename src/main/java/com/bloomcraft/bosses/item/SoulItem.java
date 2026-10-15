package com.bloomcraft.bosses.item;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.Bosses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A Boss Soul: right-click a boss with it (or throw it at one) and the boss rises. */
public class SoulItem extends Item {

	public SoulItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		Boss boss = Bosses.get().bosses().get(target);
		if (boss == null || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (Bosses.get().features().offerSoul(sp, stack, boss) && !player.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
