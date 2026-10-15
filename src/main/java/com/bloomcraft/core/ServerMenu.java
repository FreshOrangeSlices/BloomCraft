package com.bloomcraft.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * A chest-style menu run entirely by the server: it shows items as buttons, and every click goes to a handler
 * instead of moving items around (nothing can be taken out or put in).
 */
public class ServerMenu extends ChestMenu {

	/** A click on one of the menu's own slots (0 = top left). */
	public interface Handler {
		void click(ServerPlayer player, int slot, ContainerInput input, int button);
	}

	private final int size;
	private final Handler handler;

	private ServerMenu(int containerId, Inventory inventory, int rows, Container items, Handler handler) {
		super(type(rows), containerId, inventory, items, rows);
		this.size = rows * 9;
		this.handler = handler;
	}

	private static MenuType<ChestMenu> type(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** Opens a menu: {@code fill} puts the buttons in, {@code handler} hears the clicks. */
	public static void open(ServerPlayer player, Component title, int rows, Consumer<Container> fill, Handler handler) {
		int r = Math.max(1, Math.min(6, rows));
		SimpleContainer items = new SimpleContainer(r * 9);
		fill.accept(items);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ServerMenu(id, inventory, r, items, handler), title));
	}

	@Override
	public void clicked(int slot, int button, ContainerInput input, Player player) {
		if (player instanceof ServerPlayer sp && slot >= 0 && slot < size) {
			try {
				handler.click(sp, slot, input, button);
			} catch (RuntimeException ex) {
				com.bloomcraft.BloomCraft.LOG.error("A menu click failed", ex);
			}
		}
		if (player.containerMenu == this) {
			sendAllDataToRemote(); // undo whatever the client thought would happen
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
