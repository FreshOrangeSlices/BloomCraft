package com.bloomcraft.core;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Opening written books for reading without handing them out. */
public final class Books {

	private Books() {
	}

	/**
	 * Opens a book for reading without putting it in the inventory: the client briefly sees it in the held slot,
	 * opens it, and gets the real item back.
	 */
	public static void open(ServerPlayer player, ItemStack book) {
		int slot = player.getInventory().getSelectedSlot();
		int menuSlot = 36 + slot; // hotbar slots in the player's own inventory screen
		var menu = player.inventoryMenu;
		player.closeContainer();
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), menuSlot, book));
		player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), menuSlot,
			player.getInventory().getItem(slot).copy()));
	}
}
