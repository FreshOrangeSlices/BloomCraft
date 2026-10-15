package com.bloomcraft.bosses.item;

import com.bloomcraft.bosses.Bosses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A Boss Totem: right-click for a 3-second ritual, and a boss of its rank arrives to fight you. */
public class TotemItem extends Item {

	public TotemItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			ItemStack stack = player.getItemInHand(hand);
			Bosses.get().features().useTotem(sp, stack);
		}
		return InteractionResult.SUCCESS;
	}
}
