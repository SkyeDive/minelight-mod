package net.ferret;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

// Class for dealing with the data of NativeImage objects
public class NativeImageHandler {
	
	public static boolean isImageValid(@Nullable NativeImage image) {
		// invalid if it doesn't exist
		if (image == null)
			return false;
		// invalid if it isn't RGBA format or a positive size, or if it's deallocated
		try {
			image.getPixelRGBA(0, 0);
		} catch (RuntimeException re) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Get the {@link Bounds} corresponding to the smallest rectangle that includes all pixels that change between the two {@link NativeImage} params.
	 * Returns null if no pixels have changed, or if comparing them is impossible.
	 * @param old Previous frame
	 * @param cur New, possibly changed frame
	 * @return Dimension data for only changed pixels, or null if "no change"
	 */
	public static @Nullable Bounds getChangedRect(@Nullable NativeImage old, @Nullable NativeImage cur) {
		// special short-circuit
		if (old == cur)
			return null;
		// current buffer doesn't exist: return null for no change
		if (cur == null || cur.getWidth() == 0 || cur.getHeight() == 0)
			return null;
		// no previous buffer; everything has changed
		if (old == null || old.getWidth() == 0 || old.getHeight() == 0)
			return new Bounds(0, 0, cur.getWidth(), cur.getHeight());
		// buffers have resized; everything has changed
		if (old.getWidth() != cur.getWidth() || old.getHeight() != cur.getHeight())
			return new Bounds(0, 0, cur.getWidth(), cur.getHeight());
		
		/*
		assertions:
		both buffers exist
		both buffers have a size greater than 0
		both buffers have the same size
		*/
		
		// detect changes
		int[] oldA;
		int[] curA;
		try {
			oldA = old.getPixelsRGBA();
			curA = cur.getPixelsRGBA();
		} catch (RuntimeException re) {
			// final failsafe: if images were formatted badly or deallocated, return no change
			return null;
		}
		int startX = 0;
		int startY = 0;
		int endX = cur.getWidth() - 1;
		int endY = cur.getHeight() - 1;
		int width = cur.getWidth();
		boolean identical = false;
		boolean hasChanged = false;
		// bring in left border
		for (int curX = startX; curX <= endX && !hasChanged; curX++) {
			for (int curY = startY; curY <= endY; curY++) {
				int position = curX + curY * width;
				hasChanged = hasChanged || (oldA[position] != curA[position]);
			}
			if (!hasChanged)
				startX++;
		}
		if (hasChanged) {
			// bring in top border
			hasChanged = false;
			for (int curY = startY; curY <= endY && !hasChanged; curY++) {
				for (int curX = startX; curX <= endX; curX++) {
					int position = curX + curY * width;
					hasChanged = hasChanged || (oldA[position] != curA[position]);
				}
				if (!hasChanged)
					startY++;
			}
			// bring in right border
			hasChanged = false;
			for (int curX = endX; curX >= startX && !hasChanged; curX--) {
				for (int curY = startY; curY <= endY; curY++) {
					int position = curX + curY * width;
					hasChanged = hasChanged || (oldA[position] != curA[position]);
				}
				if (!hasChanged)
					endX--;
			}
			// bring in bottom border
			hasChanged = false;
			for (int curY = endY; curY >= startY && !hasChanged; curY--) {
				for (int curX = startX; curX <= endX; curX++) {
					int position = curX + curY * width;
					hasChanged = hasChanged || (oldA[position] != curA[position]);
				}
				if (!hasChanged)
					endY--;
			}
			
			// return only what has changed
			return new Bounds(startX, startY, endX - startX, endY - startY);
		}
		// no pixels changed, return no change
		return null;
	}
	
	/**
	 * Crops the image to the {@link Bounds} provided, returning a {@link NativeImage} object.
	 * May return existing object. Result must be closed manually.
	 * @param image Uncropped image
	 * @param bounds Dimensions to crop to
	 * @return Cropped image
	 */
	public static @Nullable NativeImage crop(NativeImage image, Bounds bounds) {
		// don't try to crop invalid images
		if (!isImageValid(image))
			return null;
		// don't try to crop with invalid bounds
		if (!areBoundsValid(image, bounds))
			return null;
		// shortcut if the bounds don't change anything
		if (bounds.x == 0 && bounds.y == 0 && bounds.w == image.getWidth() && bounds.h == image.getHeight())
			return image;
		
		NativeImage cropped = new NativeImage(bounds.w, bounds.h, false);
		image.copyRect(cropped, bounds.x, bounds.y, 0, 0, bounds.w, bounds.h, false, false);
		return cropped;
	}
	
	/**
	 * Constructs a new NativeImage from provided dimensions and arrayed pixels.
	 * Result must be closed manually.
	 * @param w Image width in px
	 * @param h Image height in px
	 * @param pixels Flattened array of ints representing pixels. Length must match w*h
	 * @return NativeImage object corresponding to arguments
	 */
	public static @Nullable NativeImage fromPixelArray(int w, int h, int[] pixels) {
		// return null on invalid dimensions
		if (w <= 0 || h <= 0)
			return null;
		// throw error on size mismatch
		if (w*h != pixels.length)
			throw new RuntimeException("Image dimensions (%d, %d) are invalid for pixel array size (%d)!"
				.formatted(w, h, pixels.length));
		
		NativeImage image = new NativeImage(w, h, false);
		for (int curY = 0; curY < h; curY++) {
			for (int curX = 0; curX < w; curX++) {
				int curIndex = curX + curY * w;
				image.setPixelRGBA(curX, curY, pixels[curIndex]);
			}
		}
		return image;
	}
	
	public static boolean areBoundsValid (NativeImage image, @Nullable Bounds bounds) {
		// never valid for invalid images
		if (!isImageValid(image))
			return false;
		// never valid for null bounds
		if (bounds == null)
			return false;
		
		return posInside(image, bounds.x, bounds.y) && posInside(image, bounds.x + bounds.w, bounds.y + bounds.h);
	}
	
	private static boolean posInside(NativeImage image, int x, int y) {
		return x >= 0
			&& y >= 0
			&& x < image.getWidth()
			&& y < image.getHeight();
	}
	
	public static class Bounds {
		public int x;
		public int y;
		public int w;
		public int h;
		
		public Bounds(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}
		
		public int endX() {
			return x + w;
		}
		
		public int endY() {
			return y + h;
		}
	}
}
