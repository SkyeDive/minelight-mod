package net.ferret;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

// Clientside class for possessors' copycat GUI
public class VesselScreen extends Screen implements MenuAccess<VesselScreenMenu> {
	private final VesselScreenMenu menu;
	public static DynamicTexture tex = null;
	public static ResourceLocation texLocation = null;
	public static Integer curImage = null;
	private static TextureManager tm;
	
	public VesselScreen(VesselScreenMenu vsm, Inventory inventory, Component component) {
		super(GameNarrator.NO_TITLE);
		menu = vsm;
		tm = Minecraft.getInstance().getTextureManager();
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
	
	public void resetTexture() {
		synchronized (BodySharingClient.copycatScreenSync) {
			Minecraft client = Minecraft.getInstance();
			Window window = client.getWindow();
			if (BodySharingClient.resizedScreen == null) {
				BodySharingClient.resizedScreen = new NativeImage(window.getWidth(), window.getHeight(), false);
				if (tex != null)
					tex.close();
				tex = null;
				curImage = null;
			
				if (BodySharingClient.vesselScreen != null) {
					BodySharingClient.vesselScreen.resizeSubRectTo(0, 0,
						BodySharingClient.vesselScreen.getWidth(), BodySharingClient.vesselScreen.getHeight(),
						BodySharingClient.resizedScreen);
				}
			} else if (BodySharingClient.resizedScreen.getWidth() != window.getWidth()
				|| BodySharingClient.resizedScreen.getHeight() != window.getHeight()) {
				BodySharingClient.resizedScreen.close();
				BodySharingClient.resizedScreen = new NativeImage(window.getWidth(), window.getHeight(), false);
				if (tex != null)
					tex.close();
				tex = null;
				curImage = null;
			} else if (curImage != null && BodySharingClient.resizedScreen.hashCode() != curImage) {
				if (tex != null)
					tex.close();
				tex = null;
				curImage = null;
			}
		}
	}
	
	public void updateTexture() {
		try {
			synchronized (BodySharingClient.copycatScreenSync) {
				if (tex == null) {
					if (BodySharingClient.resizedScreen != null) {
						tex = new DynamicTexture(BodySharingClient.resizedScreen);
						curImage = BodySharingClient.resizedScreen.hashCode();
					} else {
						return;
					}
				} else {
					if (curImage == null || BodySharingClient.resizedScreen.hashCode() != curImage) {
						if (BodySharingClient.resizedScreen != null) {
							tex.setPixels(BodySharingClient.resizedScreen);
							curImage = BodySharingClient.resizedScreen.hashCode();
							tex.upload();
						} else {
							tm.release(texLocation);
							tex.close();
							tex = null;
							curImage = null;
							return;
						}
					} else {
						tex.upload();
					}
				}
				tm.register(texLocation, tex);
			}
		} catch (RuntimeException re) {
			re.printStackTrace();
			// mostly copied from FaviconTexture
			if (tex != null) {
				tm.release(texLocation);
				tex.close();
				tex = null;
				curImage = null;
			}
		}
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		resetTexture();
		updateTexture();
		Minecraft client = Minecraft.getInstance();
		if (tex != null) {
			Window window = client.getWindow();
			//float w = (float)window.getGuiScaledWidth();
			//float h = (float)window.getGuiScaledHeight();
			//int tW = curImage.getWidth() / (int)window.getGuiScale();
			//int tH = curImage.getHeight() / (int)window.getGuiScale();
			int w = (int)(tex.getPixels().getWidth() / window.getGuiScale());
			int h = (int)(tex.getPixels().getHeight() / window.getGuiScale());
			//RenderSystem.enableBlend();
			// texture, screen start x & y, uv start x & y, image width & height / uv end x & y, uv divisor x & y
			guiGraphics.blit(texLocation, 0, 0, 0.0f, 0.0f, w, h, w, h);
			//guiGraphics.blit(texLocation, 0, 0, w, h, 0.0f, 0.0f, tW, tH, tW, tH);
			/*
			RenderSystem.setShaderTexture(0, texLocation);
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			Matrix4f matrix4f = guiGraphics.pose().last().pose();
			BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
			bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
			bufferBuilder.vertex(matrix4f, 0.0f, 0.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
			bufferBuilder.vertex(matrix4f, 0.0f, h, 0.0f).uv(0.0f, 1.0f).endVertex();
			bufferBuilder.vertex(matrix4f, w, h, 0.0f).uv(1.0f, 1.0f).endVertex();
			bufferBuilder.vertex(matrix4f, w, 0.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
			BodySharing.LOGGER.info("STOP");
			BufferUploader.drawWithShader(bufferBuilder.end());
			BodySharing.LOGGER.info("TALKING");
			*/
			//RenderSystem.disableBlend();
		}
	}
}
