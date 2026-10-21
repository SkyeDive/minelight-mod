package net.ferret.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.ferret.BodySharingClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;

// Mixin to enable allowlisted hotkeys to be activated when inside of a GUI
@Mixin(KeyboardHandler.class)
public class GUI_KeypressMixin {
	@Shadow
	Minecraft minecraft;
	@Inject(at = @At("HEAD"), method = "keyPress", cancellable = true)
	private void AllowGUIKeypresses(long window, int keycode, int scancode, int action, int mods, CallbackInfo ci) {
		if (window == minecraft.getWindow().getWindow()) {
			if (minecraft.screen != null) {
				InputConstants.Key hotkeyPressed = InputConstants.getKey(keycode, scancode);
				for (KeyMapping keyAction : BodySharingClient.guiKeys) {
					if (KeyBindingHelper.getBoundKeyOf(keyAction) == hotkeyPressed) {
						if (action == 0) {
							KeyMapping.set(hotkeyPressed, false);
						} else {
							KeyMapping.set(hotkeyPressed, true);
							KeyMapping.click(hotkeyPressed);
						}
						ci.cancel();
						break;
					}
				}
			}
		}
	}
}