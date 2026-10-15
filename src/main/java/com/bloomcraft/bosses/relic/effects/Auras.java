package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.item.EquipmentType;
import com.bloomcraft.bosses.relic.BaseRelic;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.function.Predicate;

/** Relics that give a potion effect while equipped (some only under a condition). */
public final class Auras {

	private Auras() {
	}

	public static Aura ironWill() {
		return new Aura("iron-will", "Iron Will", MobEffects.RESISTANCE, "Resistance", 1,
			"Grants {effect} while equipped.", p -> true);
	}

	public static Aura bloodMending() {
		return new Aura("blood-mending", "Blood Mending", MobEffects.REGENERATION, "Regeneration", 1,
			"Grants {effect} while equipped.", p -> true);
	}

	public static Aura skybound() {
		return new Aura("skybound", "Skybound", MobEffects.JUMP_BOOST, "Jump Boost", 2,
			"Grants {effect} while equipped.", p -> true);
	}

	public static Aura emberWard() {
		return new Aura("ember-ward", "Ember Ward", MobEffects.FIRE_RESISTANCE, "Fire Resistance", 1,
			"Grants {effect} while equipped.", p -> true);
	}

	public static Aura tidebound() {
		return new Aura("tidebound", "Tidebound", MobEffects.CONDUIT_POWER, "Conduit Power", 1,
			"Grants {effect} while you're in water.", ServerPlayer::isInWater);
	}

	public static Aura oceanGrace() {
		return new Aura("ocean-grace", "Ocean Grace", MobEffects.DOLPHINS_GRACE, "Dolphin's Grace", 1,
			"Grants {effect} while you're in water.", ServerPlayer::isInWater);
	}

	public static Aura villagerFavor() {
		return new Aura("villager-favor", "Villager Favor", MobEffects.HERO_OF_THE_VILLAGE, "Hero of the Village", 1,
			"Grants {effect} (better trades) while equipped.", p -> true);
	}

	public static Aura minersFavor() {
		return new Aura("miners-favor", "Miner's Favor", MobEffects.HASTE, "Haste", 1,
			"Grants {effect} while holding a tool or underground.",
			p -> EquipmentType.of(p.getMainHandItem()) == EquipmentType.TOOL
				|| RelicUtil.skyLight(p.level(), BlockPos.containing(p.getEyePosition())) == 0);
	}

	public static Aura nightstalker() {
		return new Aura("nightstalker", "Nightstalker", MobEffects.SPEED, "Speed", 1,
			"Grants {effect} at night or in the dark.",
			p -> RelicUtil.isNight(p.level()) || RelicUtil.light(p.level(), BlockPos.containing(p.getEyePosition())) <= 7);
	}

	public static Aura sunblessed() {
		return new Aura("sunblessed", "Sunblessed", MobEffects.REGENERATION, "Regeneration", 1,
			"Grants {effect} while you stand in sunlight.",
			p -> RelicUtil.overworld(p.level()) && !RelicUtil.isNight(p.level()) && !p.level().isRaining()
				&& RelicUtil.skyLight(p.level(), BlockPos.containing(p.getEyePosition())) >= 15);
	}

	/** One configurable potion aura. */
	public static final class Aura extends BaseRelic {
		private final Holder<MobEffect> type;
		private final String effectName;
		private final int defaultLevel;
		private final String template;
		private final Predicate<ServerPlayer> condition;
		private int level;

		Aura(String id, String name, Holder<MobEffect> type, String effectName, int defaultLevel, String template,
			 Predicate<ServerPlayer> condition) {
			super(id, name, false);
			this.type = type;
			this.effectName = effectName;
			this.defaultLevel = defaultLevel;
			this.level = defaultLevel;
			this.template = template;
			this.condition = condition;
		}

		@Override
		public void load(Cfg s) {
			level = Math.max(1, s.getInt("level", defaultLevel));
		}

		@Override
		public String description() {
			return template.replace("{effect}", effectName + " " + Text.roman(level));
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (!condition.test(p)) {
				return;
			}
			// short and constantly refreshed: it fades a few seconds after the condition stops
			MobEffectInstance current = p.getEffect(type);
			if (current == null || (current.getAmplifier() <= level - 1 && !current.isInfiniteDuration()
				&& current.getDuration() < 60)) {
				p.addEffect(new MobEffectInstance(type, 100, level - 1, true, false, true));
			}
		}
	}
}
