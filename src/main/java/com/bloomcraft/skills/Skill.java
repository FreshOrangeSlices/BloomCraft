package com.bloomcraft.skills;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The skills a player levels up by doing things. Taming comes first (pets need it); the others arrive with the
 * full skill system.
 */
public enum Skill {

	TAMING("Taming", 0xE8A15A, "Bond with animals and fight beside your combat pets.");

	private final String displayName;
	private final int color;
	private final String description;

	Skill(String displayName, int color, String description) {
		this.displayName = displayName;
		this.color = color;
		this.description = description;
	}

	public String displayName() {
		return displayName;
	}

	public int color() {
		return color;
	}

	public String description() {
		return description;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable Skill parse(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		for (Skill s : values()) {
			if (s.name().equals(key)) {
				return s;
			}
		}
		return null;
	}
}
