package net.ferret;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

// Serverside class for possessors' copycat GUI
public class VesselScreenMenu extends AbstractContainerMenu {
	
	// dummy
	public VesselScreenMenu(int i, Inventory inventory) {
		super(BodySharing.VESSEL_SCREEN_MENU, i);
	}
	
	public VesselScreenMenu(int i) {
		this(i, null);
	}
	
	// disable item movement (there are no slots)
	@Override
	public ItemStack quickMoveStack(Player player, int i) {
		return ItemStack.EMPTY;
	}
	
	// dummy
	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
