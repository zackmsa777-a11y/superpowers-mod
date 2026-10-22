package com.superpowers.power;

/**
 * The five powers. Slots are 0 = M1, 1 = Z, 2 = X, 3 = C, 4 = V (ultimate).
 */
public enum PowerType {
	NONE("none", "None", 0xFFFFFF, 0xFFFFFF, "", 0,
			new String[]{"", "", "", "", ""},
			new String[]{"", "", "", "", ""}),

	FIRE("fire", "Fire", 0xFF6A1A, 0xFFD24A,
			"Wield the eternal flame. Burn, dash and erupt - then forge a star in your hands.", 110,
			new String[]{"Flame Fists", "Blaze Orb", "Flame Dash", "Inferno Ring", "SUPERNOVA"},
			new String[]{
					"4-hit combo of burning punches. The last hit erupts.",
					"Hurl a roaring fireball that explodes on impact.",
					"Blast forward leaving a trail of fire.",
					"Pillars of flame erupt in an expanding ring.",
					"Rise, forge a miniature sun and hurl it down."},
			"INFERNAL SOVEREIGN", "Kill enemies while they burn, and set the world ablaze."),

	SPACE("space", "Space", 0x9A5CFF, 0x5CD6FF,
			"Bend gravity and spacetime. Warp, crush and summon the stars themselves.", 140,
			new String[]{"Gravity Palm", "Warp", "Gravity Well", "Meteor Shower", "BIG BANG"},
			new String[]{
					"4-hit combo of gravity strikes. The last hit implodes.",
					"Tear through space and teleport where you look.",
					"Lift every enemy into the air, then crush them down.",
					"Call down a storm of meteors on your target.",
					"Open a black hole, devour everything, then explode."},
			"COSMIC ARBITER", "Kill enemies while they are airborne or with falls."),

	ANGEL("angel", "Angel", 0xFFF4C2, 0x8FE3FF,
			"Heaven's chosen warrior. Holy blades, radiant wings and the light of judgment.", 120,
			new String[]{"Holy Blade", "Seraph Wings", "Holy Lances", "Sanctuary", "JUDGMENT"},
			new String[]{
					"4-hit combo of radiant slashes. The last is a cross.",
					"Spread wings of light: launch up, fly fast, heal.",
					"Summon lances of light that home in on enemies.",
					"Consecrate the ground - heals you, burns enemies.",
					"A halo opens the sky and a pillar of light descends."},
			"SERAPH ASCENDANT", "Slay hostile monsters."),

	DEMON("demon", "Demon", 0xE0102F, 0x5A0010,
			"Born of hellfire. Claw, chain and drain your foes - then unleash Pandemonium.", 120,
			new String[]{"Demon Claws", "Soul Chain", "Blood Rush", "Hell Spikes", "PANDEMONIUM"},
			new String[]{
					"4-hit combo of claw slashes that steal life.",
					"Chain an enemy and drag it to you (or grapple a wall).",
					"Dash through enemies, draining their blood.",
					"A line of hell spikes erupts from the ground.",
					"Become the Demon King. A pentagram of hell erupts."},
			"DEMON KING", "Kill villagers and animals."),

	GOD("god", "God", 0xFFC93C, 0xFFFFFF,
			"Omnipotent. Smite with lightning, stop time itself and judge all creation.", 130,
			new String[]{"Divine Palm", "Smite", "Time Stop", "Heaven's Arsenal", "LET THERE BE LIGHT"},
			new String[]{
					"4-hit combo of divine palms. The last is a shockwave.",
					"Strike your target with golden lightning.",
					"Freeze every creature around you in time.",
					"Summon golden swords that rain on your target.",
					"Ascend in golden rings and smite everything around you."},
			"ABSOLUTE DIVINITY", "Judge any creature - stronger foes fill it faster."),

	ENTITY("entity", "Entity 666", 0xB0101A, 0x1A0A0C,
			"Something wearing a person's shape. Hunt in the dark, trap them in your domain, and feed.", 150,
			new String[]{"Rend", "Flicker", "Mimic", "The Hollow", "THE FEAST"},
			new String[]{
					"4-hit combo of long claws. The last hit drags them to you.",
					"Vanish and reappear right behind your target.",
					"Release fake mobs. Whoever kills one finds you in front of them.",
					"Trap everyone near you in darkness. You see them. They can't see you.",
					"Rise behind your prey, tear its head off and feed."},
			"ENTITY 666", "Kill in the dark or inside your domain. Players count more.");

	public static final String[] KEYS = {"M1", "Z", "X", "C", "V"};
	public static final int[] COST = {0, 15, 20, 30, 100};
	public static final float MAX_MANA = 100f;

	public final String id;
	public final String displayName;
	/** 0xRRGGBB */
	public final int color;
	public final int color2;
	public final String description;
	public final int ultDuration;
	public final String[] abilityNames;
	public final String[] abilityDescs;
	/** Name of the awakened form. */
	public final String awakenedName;
	/** How this power fills its awakening meter. */
	public final String meterHint;

	public static final float MAX_METER = 100f;
	/** Awakening lasts this long in survival (creative: until F). */
	public static final int AWAKEN_TICKS = 1200;
	public static final int AWAKEN_CUTSCENE = 100;

	PowerType(String id, String displayName, int color, int color2, String description, int ultDuration, String[] abilityNames, String[] abilityDescs) {
		this(id, displayName, color, color2, description, ultDuration, abilityNames, abilityDescs, "", "");
	}

	PowerType(String id, String displayName, int color, int color2, String description, int ultDuration, String[] abilityNames, String[] abilityDescs,
			  String awakenedName, String meterHint) {
		this.awakenedName = awakenedName;
		this.meterHint = meterHint;
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.color2 = color2;
		this.description = description;
		this.ultDuration = ultDuration;
		this.abilityNames = abilityNames;
		this.abilityDescs = abilityDescs;
	}

	/** Every power flies, but only while awakened. */
	public boolean canFly(boolean awakened) {
		return awakened && this != NONE;
	}

	/** Abilities flying speed (vanilla creative is 0.05, sprinting doubles it). */
	public float flySpeed(boolean awakened) {
		float base = switch (this) {
			case GOD -> 0.11f;
			case SPACE -> 0.085f;
			case FIRE -> 0.09f;
			case DEMON -> 0.08f;
			default -> 0.07f;
		};
		return awakened ? base * 1.4f : base;
	}

	/** Cooldown in ticks for a slot. */
	public int cooldown(int slot) {
		return switch (slot) {
			case 0 -> 5;
			case 1 -> 24;
			case 2 -> 50;
			case 3 -> 80;
			default -> ultDuration + 60;
		};
	}

	public static PowerType byId(String id) {
		for (PowerType t : values()) {
			if (t.id.equals(id)) return t;
		}
		return NONE;
	}

	public static PowerType byOrdinal(int i) {
		PowerType[] v = values();
		return i >= 0 && i < v.length ? v[i] : NONE;
	}
}
