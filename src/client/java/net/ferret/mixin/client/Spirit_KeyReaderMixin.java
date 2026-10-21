package net.ferret.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.ferret.BodySharing;
import net.ferret.BodySharingClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

// Mixin for reading keypress and chartype events from possessors
@Mixin(KeyboardHandler.class)
public abstract class Spirit_KeyReaderMixin {
	@Shadow
	Minecraft minecraft;
	
	private static boolean keyEqual(InputConstants.Key a, InputConstants.Key b) {
		return a.getType() == b.getType() && a.getValue() == b.getValue();
	}
	
	// Capture key presses and holds of possessor
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	public void interceptKeyPress(long window, int keysym, int scancode, int action, int mods, CallbackInfo ci) {
		if (BodySharingClient.isPossessing) {
			// Ensure the key press has a keysym
			if (keysym == -1)
				return;
			// Cancel if the key pressed is the stop-possession hotkey
			InputConstants.Key cur = InputConstants.getKey(keysym, scancode);
			InputConstants.Key stopPossession = ((KeyMappingAccess)BodySharingClient.stopPossession).bodySharingGetKey();
			if (keyEqual(cur, stopPossession))
				return;
			
			// Add to self records and send keypress event
			boolean newVal = action == 0 ? false : true;
			boolean oldVal = BodySharingClient.selfKeyRecord.getOrDefault(keysym, false);
			BodySharingClient.selfKeyRecord.put(keysym, newVal);
			if (!BodySharingClient.selfUpdatedKeys.contains(keysym) && newVal != oldVal)
				BodySharingClient.selfUpdatedKeys.add(keysym);
			if (minecraft.isRunning() && minecraft.getCurrentServer() != null) {
				FriendlyByteBuf packet = PacketByteBufs.create();
				packet.writeInt(keysym);
				packet.writeInt(scancode);
				packet.writeInt(action);
				packet.writeInt(mods);
				ClientPlayNetworking.send(BodySharing.KEY_PRESS_ID, packet);
			}
			ci.cancel();
		}
	}
	
	// Capture characters typed by possessor
	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	public void interceptCharTyped(long window, int codepoint, int mods, CallbackInfo ci) {
		if (BodySharingClient.isPossessing && minecraft.isRunning() && minecraft.getCurrentServer() != null) {
			FriendlyByteBuf packet = PacketByteBufs.create();
			packet.writeInt(codepoint);
			packet.writeInt(mods);
			ClientPlayNetworking.send(BodySharing.CHAR_TYPE_ID, packet);
			ci.cancel();
		}
	}
}
