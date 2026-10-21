package net.ferret.mixin;

import java.util.OptionalInt;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.ferret.BodySharing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

// Mixin for bouncing the opening and closing of GUIs from vessel to possessors
@Mixin(ServerPlayer.class)
public abstract class GuiCopyMixin {
	
	@Inject(at = @At("RETURN"), method = "openMenu", cancellable = true)
	public void copyOpenMenu(@Nullable MenuProvider menuProvider, CallbackInfoReturnable<OptionalInt> cir) {
		if (cir.getReturnValue() != OptionalInt.empty()) {
			// this player's uuid
			UUID playerUUID = ((ServerPlayer)(Object)this).getUUID();
			if (BodySharing.isHosting(playerUUID)) {
				for (UUID spiritPlayer : BodySharing.getPossessors(playerUUID)) {
					((ServerPlayer)(Object)this).server.getPlayerList().getPlayer(spiritPlayer).openMenu(menuProvider);
				}
			}
		}
	}
	
	@Inject(at = @At("RETURN"), method = "openHorseInventory")
	public void copyOpenHorseInventory(AbstractHorse abstractHorse, Container container, CallbackInfo ci) {
		// this player's uuid
		UUID playerUUID = ((ServerPlayer)(Object)this).getUUID();
		if (BodySharing.isHosting(playerUUID)) {
			for (UUID spiritPlayer : BodySharing.getPossessors(playerUUID)) {
				((ServerPlayer)(Object)this).server.getPlayerList().getPlayer(spiritPlayer).openHorseInventory(abstractHorse, container);
			}
		}
	}
	
	@Inject(at = @At("RETURN"), method = "doCloseContainer")
	public void copyCloseContainer(CallbackInfo ci) {
		// this player's uuid
		UUID playerUUID = ((ServerPlayer)(Object)this).getUUID();
		if (BodySharing.isHosting(playerUUID)) {
			for (UUID spiritPlayer : BodySharing.getPossessors(playerUUID)) {
				((ServerPlayer)(Object)this).server.getPlayerList().getPlayer(spiritPlayer).closeContainer();
			}
		}
	}
}
