package com.bloomcraft.core;

import com.bloomcraft.BloomCraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/** The mod's own data on an item stack (in its custom data, under "bloomcraft"). */
public final class ItemData {

	private ItemData() {
	}

	/** A copy of the item's data (empty if it has none). */
	public static CompoundTag of(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		return custom == null ? new CompoundTag() : custom.copyTag().getCompoundOrEmpty(BloomCraft.MOD_ID);
	}

	public static void edit(ItemStack stack, Consumer<CompoundTag> change) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
			CompoundTag tag = root.getCompoundOrEmpty(BloomCraft.MOD_ID);
			change.accept(tag);
			root.put(BloomCraft.MOD_ID, tag);
		});
	}

	/** Removes the mod's data from the item (it becomes a plain item of its kind). */
	public static void clear(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.remove(BloomCraft.MOD_ID));
	}
}
