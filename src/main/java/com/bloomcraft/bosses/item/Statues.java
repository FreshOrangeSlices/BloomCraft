package com.bloomcraft.bosses.item;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.nemesis.NemesisRecord;
import com.bloomcraft.core.Clock;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Nemesis Statues: an item that places the slain Nemesis as a small, frozen, unkillable figure. Sneak and use it
 * with an empty hand (or the block under it) to pick it back up.
 */
public final class Statues {

	/** What the mob had on, slot by slot. */
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
		EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private final Bosses bosses;

	public Statues(Bosses bosses) {
		this.bosses = bosses;
	}

	private static RegistryOps<Tag> ops(ServerLevel level) {
		return level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	/** The statue item for a slain Nemesis. */
	public @Nullable ItemStack create(Boss boss, NemesisRecord r, Component name) {
		LivingEntity e = boss.entity();
		ServerLevel level = boss.level();
		ItemStack item = new ItemStack(ModItems.NEMESIS_STATUE);
		item.set(DataComponents.ITEM_NAME, name);
		item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		// it looks like the mob's own spawn egg
		Identifier egg = Identifier.withDefaultNamespace(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath() + "_spawn_egg");
		if (BuiltInRegistries.ITEM.containsKey(egg)) {
			item.set(DataComponents.ITEM_MODEL, egg);
		}
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("Nemesis Statue", ChatFormatting.WHITE));
		lore.add(Text.line("Level " + r.level + " · killed its prey " + r.kills + "× · fled " + r.escapes + "×",
			ChatFormatting.GRAY));
		lore.add(Text.line("Hunted " + r.ownerName + " · slain on day " + Clock.day(), ChatFormatting.DARK_GRAY));
		lore.add(Component.empty());
		lore.add(Text.line("Use it on a block to place it.", ChatFormatting.DARK_GRAY));
		lore.add(Text.line("Sneak + use it (or the block under it)", ChatFormatting.DARK_GRAY));
		lore.add(Text.line("with an empty hand to pick it up.", ChatFormatting.DARK_GRAY));
		item.set(DataComponents.LORE, new ItemLore(lore));
		ListTag gear = new ListTag();
		RegistryOps<Tag> ops = ops(level);
		for (EquipmentSlot slot : SLOTS) {
			ItemStack worn = e.getItemBySlot(slot);
			gear.add(worn.isEmpty() ? new CompoundTag()
				: ItemStack.CODEC.encodeStart(ops, worn.copyWithCount(1)).result().orElse(new CompoundTag()));
		}
		Tag nameTag = ComponentSerialization.CODEC.encodeStart(ops, name).result().orElse(null);
		BossItems.edit(item, tag -> {
			tag.putString("kind", "statue");
			tag.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
			tag.putBoolean("baby", e.isBaby());
			tag.put("gear", gear);
			if (nameTag != null) {
				tag.put("name", nameTag);
			}
		});
		return item;
	}

	/** Places the statue figure. Returns false if the item is broken or the spot is blocked. */
	public boolean place(ServerLevel level, ItemStack item, Vec3 at, float yaw) {
		CompoundTag data = BossItems.data(item);
		Identifier typeId = Identifier.tryParse(data.getStringOr("type", ""));
		EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
		if (type == null) {
			return false;
		}
		Entity created = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (!(created instanceof Mob statue)) {
			if (created != null) {
				created.discard();
			}
			return false;
		}
		RegistryOps<Tag> ops = ops(level);
		statue.snapTo(at.x, at.y, at.z, yaw, 0);
		statue.setYHeadRot(yaw);
		statue.setYBodyRot(yaw);
		Data.of(statue).putString(BossKeys.STATUE, "nemesis");
		ItemStack original = item.copyWithCount(1);
		ItemStack.CODEC.encodeStart(ops, original).result().ifPresent(t -> Data.of(statue).put("statue_item", t));
		statue.setNoAi(true);
		statue.setSilent(true);
		statue.setPermanentlyInvulnerable(true);
		statue.setNoGravity(true);
		statue.setPersistenceRequired();
		statue.setCanPickUpLoot(false);
		statue.setBaby(data.getBooleanOr("baby", false));
		Tag nameTag = data.get("name");
		Component name = nameTag == null ? null : ComponentSerialization.CODEC.parse(ops, nameTag).result().orElse(null);
		if (name != null) {
			statue.setCustomName(name);
			statue.setCustomNameVisible(false); // the name shows when you look at it
		}
		ListTag gear = data.getListOrEmpty("gear");
		for (int i = 0; i < Math.min(gear.size(), SLOTS.length); i++) {
			Tag t = gear.get(i);
			if (t instanceof CompoundTag c && c.isEmpty()) {
				continue;
			}
			ItemStack piece = ItemStack.CODEC.parse(ops, t).result().orElse(ItemStack.EMPTY);
			if (!piece.isEmpty()) {
				statue.setItemSlot(SLOTS[i], piece);
				statue.setDropChance(SLOTS[i], 0f);
			}
		}
		if (statue instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		if (statue instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
		resize(statue);
		if (!level.addFreshEntity(statue)) {
			return false;
		}
		Fx.particle(level, ParticleTypes.CLOUD, at.add(0, 0.6, 0), 15, 0.4, 0.02);
		Fx.play(level, at, "block.stone.place", 1.0f, 0.8f);
		return true;
	}

	/** Placed statues always use the current size (only their base size counts, never a modifier). */
	public void resize(Entity e) {
		if (!(e instanceof LivingEntity living) || !isStatue(e)) {
			return;
		}
		AttributeInstance scale = living.getAttribute(Attributes.SCALE);
		if (scale == null) {
			return;
		}
		scale.removeModifiers();
		double wanted = bosses.settings() == null ? 0.5 : bosses.settings().statueScale;
		if (Math.abs(scale.getBaseValue() - wanted) > 1.0E-3) {
			scale.setBaseValue(wanted);
		}
		living.setCustomNameVisible(false);
	}

	public static boolean isStatue(Entity entity) {
		return Data.has(entity, BossKeys.STATUE);
	}

	/** Small figures are hard to click, so they can also be picked up via the block below them. */
	public @Nullable Entity figureOn(ServerLevel level, BlockPos block) {
		Vec3 top = Vec3.atBottomCenterOf(block.above());
		AABB box = new AABB(top.x - 0.6, top.y - 0.2, top.z - 0.6, top.x + 0.6, top.y + 1.2, top.z + 0.6);
		List<Entity> found = level.getEntities((Entity) null, box, Statues::isStatue);
		return found.isEmpty() ? null : found.getFirst();
	}

	/** Turns a placed statue back into its item. */
	public @Nullable ItemStack pickUp(Entity statue) {
		if (!isStatue(statue) || !(statue.level() instanceof ServerLevel level)) {
			return null;
		}
		CompoundTag data = Data.of(statue);
		Tag stored = data.get("statue_item");
		ItemStack item = stored == null ? ItemStack.EMPTY
			: ItemStack.CODEC.parse(ops(level), stored).result().orElse(ItemStack.EMPTY);
		if (item.isEmpty()) {
			return null;
		}
		Fx.particle(level, ParticleTypes.CLOUD, Fx.center(statue), 15, 0.4, 0.02);
		statue.discard();
		return item;
	}
}
