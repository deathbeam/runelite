package net.runelite.client.util;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.LookupOp;
import java.awt.image.LookupTable;
import java.awt.image.PixelGrabber;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;

@Slf4j
public class Images
{
	/**
	 * Offsets an image in the grayscale (darkens/brightens) by an offset
	 */
	public static BufferedImage grayscaleOffset(BufferedImage image, int offset)
	{
		int numComponents = image.getColorModel().getNumComponents();
		int index = numComponents - 1;

		LookupTable lookup = new LookupTable(0, numComponents)
		{
			@Override
			public int[] lookupPixel(int[] src, int[] dest)
			{
				if (dest[index] != 0)
				{
					dest[index] = dest[index] + offset;
					if (dest[index] < 0)
					{
						dest[index] = 0;
					}
					else if (dest[index] > 255)
					{
						dest[index] = 255;
					}
				}

				return dest;
			}
		};

		LookupOp op = new LookupOp(lookup, new RenderingHints(null));
		return op.filter(image, null);
	}

	/**
	 * Loads image resource using class as loader
	 *
	 * @param clazz loader class
	 * @param path  path
	 * @return buffered image
	 */
	public static BufferedImage getImage(final Class<?> clazz, final String path)
	{
		try (InputStream inputStream = clazz.getResourceAsStream(path))
		{
			log.debug("Loading image: " + path);

			synchronized (ImageIO.class)
			{
				return ImageIO.read(inputStream);
			}
		}
		catch (IOException ex)
		{
			log.debug("Unable to load image: ", ex);
		}

		return null;
	}

	/**
	 * Converts buffered image to sprite pixels
	 *
	 * @param client client
	 * @param image  image
	 * @return sprite pixels
	 */
	public static SpritePixels getImageSpritePixels(final Client client, @Nullable final BufferedImage image)
	{
		if (image == null)
		{
			return null;
		}

		int[] pixels = new int[image.getWidth() * image.getHeight()];

		try
		{
			new PixelGrabber(image, 0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth())
				.grabPixels();
		}
		catch (InterruptedException ex)
		{
			log.debug("PixelGrabber was interrupted: ", ex);
		}

		return client.createSpritePixels(pixels, image.getWidth(), image.getHeight());
	}
}
