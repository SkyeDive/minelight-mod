package net.ferret.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

// Accessor mixin for MouseHandler
@Mixin(MouseHandler.class)
public interface MouseHandlerAccess {
	@Invoker("onMove")
	void bodySharingOnMove(long window, double d, double e);
	
	@Invoker("onPress")
	void bodySharingOnPress(long window, int i, int j, int k);
	
	@Invoker("onScroll")
	void bodySharingOnScroll(long window, double d, double e);
	
	@Accessor("ignoreFirstMove")
	public boolean bodySharingIgnoreFirstMove();
	
	@Accessor("ignoreFirstMove")
	public void bodySharingSetIgnoreFirstMove(boolean ignoreFirstMove);
	
	@Accessor("minecraft")
	public Minecraft bodySharingMinecraft();
}
