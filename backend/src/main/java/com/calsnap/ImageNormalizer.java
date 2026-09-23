package com.calsnap;

import static com.calsnap.Models.bad;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;

public final class ImageNormalizer {
  private ImageNormalizer() {}

  public static byte[] jpeg(byte[] input) throws IOException {
    if (input.length == 0 || input.length > 5 * 1024 * 1024)
      throw bad("Upload a JPEG or PNG under 5 MB");
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw bad("This photo format is unsupported. Choose JPEG or PNG.");
      var reader = readers.next();
      try {
        reader.setInput(stream);
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png"))
          throw bad("Choose JPEG or PNG");
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if ((long) w * h > 24000000) throw bad("Photo resolution is too large");
        BufferedImage source = reader.read(0);
        double scale = Math.min(1, 1024.0 / Math.max(w, h));
        BufferedImage dest =
            new BufferedImage(
                Math.max(1, (int) (w * scale)),
                Math.max(1, (int) (h * scale)),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, dest.getWidth(), dest.getHeight());
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, dest.getWidth(), dest.getHeight(), null);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(dest, "jpeg", out);
        return out.toByteArray();
      } finally {
        reader.dispose();
      }
    }
  }
}
