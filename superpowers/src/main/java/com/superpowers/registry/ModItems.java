package com.superpowers.registry;

import com.superpowers.Superpowers;
import com.superpowers.item.PowerTomeItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	private ModItems() {}

	public static final Item POWER_TOME = Registry.register(BuiltInRegistries.ITEM, Superpowers.id("power_tome"),
			new PowerTomeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> e.accept(POWER_TOME));
	}
}
