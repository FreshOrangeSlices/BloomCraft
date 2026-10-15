package com.bloomcraft.bosses.item;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.core.Fx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Runes, relics and catalysts: pick one up in your inventory and click it onto a piece of equipment.
 * (In creative mode the inventory works client-side, so there use /bosses apply.)
 */
public class ApplyItem extends Item {

	public ApplyItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack carried, Slot slot, ClickAction action, Player player) {
		ItemStack target = slot.getItem();
		if (target.isEmpty() || !slot.allowModification(player)) {
			return false;
		}
		if (!EquipmentType.of(target).isEquipment() && !BossItems.isCompassUpgrade(carried, target)) {
			return false; // normal inventory behaviour (putting it into a slot, a chest...)
		}
		if (player.isCreative()) {
			return false;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return true; // the server decides
		}
		Component error = BossItems.validate(carried, target);
		if (error != null) {
			Fx.actionBar(sp, error);
			Fx.playTo(sp, "block.note_block.bass", 0.8f, 0.6f);
			return true;
		}
		Component confirm = BossItems.confirmationPrompt(sp, carried, target, "slot" + slot.index);
		if (confirm != null) {
			Fx.actionBar(sp, confirm);
			Fx.playTo(sp, "block.note_block.pling", 0.8f, 1.2f);
			return true;
		}
		ItemStack updated = target.copy();
		Component message = BossItems.apply(sp, carried, updated);
		slot.set(updated);
		carried.shrink(1);
		Fx.actionBar(sp, message);
		Bosses.get().relics().refresh(sp);
		return true;
	}
}
