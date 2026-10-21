package net.ferret.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

// Accessor mixin for KeyMapping
@Mixin(KeyMapping.class)
public interface KeyMappingAccess {
	@Accessor("key")
	InputConstants.Key bodySharingGetKey();
}
