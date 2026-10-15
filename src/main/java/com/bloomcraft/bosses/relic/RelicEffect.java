package com.bloomcraft.bosses.relic;

import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.core.Cfg;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/** A relic's ability (or a curse) bound to equipment. */
public interface RelicEffect {

	String id();

	String displayName();

	/** One short sentence for the guide. */
	String description();

	boolean curse();

	default void load(Cfg section) {
	}

	default double defaultWeight() {
		return 10;
	}

	/** True if {@link #onPassive} runs about once a second while equipped. */
	default boolean passive() {
		return false;
	}

	default void onAttack(RelicContext ctx, LivingEntity victim, DamageContext damage, boolean victimIsBoss) {
	}

	default void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
	}

	default void onDamaged(RelicContext ctx, DamageContext damage) {
	}

	default void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
	}

	default void onKill(RelicContext ctx, LivingEntity victim, boolean victimWasBoss) {
	}

	/** Return true to cheat death (only asked when the player has no totem). */
	default boolean onLethal(RelicContext ctx) {
		return false;
	}

	default void onPassive(RelicContext ctx) {
	}

	default void onJump(RelicContext ctx) {
	}

	default boolean preventsSleep() {
		return false;
	}

	/** A stat change held while this relic is active (removed automatically when it isn't). */
	record AttributeBonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
	}

	default List<AttributeBonus> attributeBonuses() {
		return List.of();
	}

	/** This relic stopped being active for a player (unequipped, logged out). */
	default void onDeactivate(ServerPlayer player) {
	}

	/** Makes the curse's random event happen now (for /bosses curse). False if it has no single event. */
	default boolean trigger(ServerPlayer player, RelicManager manager) {
		return false;
	}

	/** Multiplies the killer's boss reward chances. */
	default double rewardChanceMultiplier() {
		return 1.0;
	}
}
