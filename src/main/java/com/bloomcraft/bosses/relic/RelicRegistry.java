package com.bloomcraft.bosses.relic;

import com.bloomcraft.bosses.relic.effects.Auras;
import com.bloomcraft.bosses.relic.effects.Burdened;
import com.bloomcraft.bosses.relic.effects.Curses;
import com.bloomcraft.bosses.relic.effects.HauntCurses;
import com.bloomcraft.bosses.relic.effects.MoreCurses;
import com.bloomcraft.bosses.relic.effects.NatureCurses;
import com.bloomcraft.bosses.relic.effects.Relics;

/** The built-in relics and curses. */
final class RelicRegistry {

	private RelicRegistry() {
	}

	static void registerDefaults(RelicManager m) {
		m.register(new Relics.BloodPact());
		m.register(new Relics.SoulHarvest());
		m.register(new Relics.SecondDawn());
		m.register(new Relics.BrambleHeart());
		m.register(new Relics.Emberheart());
		m.register(new Relics.Frostbite());
		m.register(new Relics.Stormcaller());
		m.register(new Relics.Windstep());
		m.register(new Relics.HuntersMark());
		m.register(new Relics.EchoStrike());
		m.register(new Relics.OwlsSight());
		m.register(new Relics.Lodestone());
		m.register(new Relics.Aegis());
		m.register(new Relics.Featherweight());
		m.register(new Relics.Prospector());
		m.register(Auras.ironWill());
		m.register(Auras.bloodMending());
		m.register(Auras.skybound());
		m.register(Auras.emberWard());
		m.register(Auras.tidebound());
		m.register(Auras.oceanGrace());
		m.register(Auras.villagerFavor());
		m.register(Auras.minersFavor());
		m.register(Auras.nightstalker());
		m.register(Auras.sunblessed());
		m.register(new Burdened.Greed());
		m.register(new Burdened.HeavyCrown());

		m.register(new Curses.Dread());
		m.register(new Curses.Butterfingers());
		m.register(new Curses.FowlOmen());
		m.register(new Curses.Insomnia());
		m.register(new Curses.Limelight());
		m.register(new Curses.Gluttony());
		m.register(new Curses.Recoil());
		m.register(new Curses.GlassBones());
		m.register(new Curses.BloodTithe());
		m.register(new MoreCurses.Terror());
		m.register(new MoreCurses.Echoes());
		m.register(new MoreCurses.Reduction());
		m.register(new MoreCurses.Matador());
		m.register(new MoreCurses.MotherHen());
		m.register(new NatureCurses.Pariah());
		m.register(new NatureCurses.Herbivore());
		m.register(new HauntCurses.WitheringWaters());
		m.register(new HauntCurses.UninvitedGuest());
		m.register(new HauntCurses.DontBlink());
		m.register(new HauntCurses.Poltergeist());
		m.register(new HauntCurses.RestlessDead());
		m.register(new HauntCurses.Hiccups());
	}
}
