package net.ferret;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

// Serverside factory for possessors' copycat GUI
public class VSMProvider implements MenuProvider {

	@Override
	public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
		return new VesselScreenMenu(i);
	}

	@Override
	public Component getDisplayName() {
		return Component.empty();
	}
}
