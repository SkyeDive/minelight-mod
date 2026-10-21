package net.ferret.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.KeyboardHandler;

// Accessor mixin for KeyboardHandler
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccess {
	@Invoker("charTyped")
	void bodySharingCharTyped(long window, int codepoint, int mods);
}
