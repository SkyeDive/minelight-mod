package net.ferret;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

// Clientside class for possessors' copycat GUI
public class VesselScreen extends Screen implements MenuAccess<VesselScreenMenu> {
	private final VesselScreenMenu menu;
	
	public VesselScreen(VesselScreenMenu vsm, Inventory inventory, Component component) {
		super(GameNarrator.NO_TITLE);
		menu = vsm;
	}
	
	@Override
	public VesselScreenMenu getMenu() {
		return menu;
	}
	
	// Don't pause the game when the GUI is open
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	
	// Handle keypresses normally, unless they're escape or the stop-possessing hotkey
	@Override
	public boolean keyPressed(int key, int scancode, int mods) {
		BodySharing.LOGGER.info(((Boolean)BodySharingClient.stopPossession.matches(key, scancode)).toString());
		if (key != InputConstants.KEY_ESCAPE && super.keyPressed(key, scancode, mods))
			return true;
		if (BodySharingClient.stopPossession.matches(key, scancode)) {
			this.onClose();
			return true;
		}
		return false;
	}
}
