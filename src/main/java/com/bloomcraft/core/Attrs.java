package com.bloomcraft.core;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Attribute modifier helpers (modifiers are saved with the entity). */
public final class Attrs {

	private Attrs() {
	}

	/** Replaces the modifier with this id (removes it when the amount is ~0). */
	public static void set(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount,
						   AttributeModifier.Operation op) {
		AttributeInstance inst = e.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		if (Math.abs(amount) > 1.0E-6) {
			inst.addPermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** Like {@link #set}, but the modifier is never saved (it's re-applied whenever it matters). */
	public static void setTransient(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount,
									AttributeModifier.Operation op) {
		AttributeInstance inst = e.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		if (Math.abs(amount) > 1.0E-6) {
			inst.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	public static void remove(LivingEntity e, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance inst = e.getAttribute(attribute);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}

	public static boolean has(LivingEntity e, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance inst = e.getAttribute(attribute);
		return inst != null && inst.hasModifier(id);
	}

	public static double value(LivingEntity e, Holder<Attribute> attribute, double def) {
		AttributeInstance inst = e.getAttribute(attribute);
		return inst == null ? def : inst.getValue();
	}
}
