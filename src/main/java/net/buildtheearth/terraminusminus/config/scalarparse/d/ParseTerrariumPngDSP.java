package net.buildtheearth.terraminusminus.config.scalarparse.d;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.util.PerformanceTracker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.IOException;

import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * @author DaPorkchop_
 */
@JsonDeserialize
public class ParseTerrariumPngDSP implements DoubleScalarParser {

    /**
     * Parses a {@link ByteBuf} of a PNG to a double array of elevation data.
     * The elevation data is retrieved based on the RGB value at a given pixel.
     *
     * @param resolution the image resolution
     * @param buffer     the byte buffer
     * @return the double array of elevations
     * @throws IOException exception
     */
    @Override
    public double[] parse(int resolution, @NonNull ByteBuf buffer) throws IOException {
        long startTime = System.nanoTime();
        try {
            BufferedImage image = ImageIO.read(new ByteBufInputStream(buffer));

            int w = image.getWidth();
            int h = image.getHeight();
            checkArg(w == resolution && h == resolution, "invalid image resolution: %dx%d (expected: %dx%3$d)", w, h, resolution);

            double[] out = new double[resolution * resolution];
            WritableRaster raster = image.getRaster();
            DataBufferByte dataBuffer = (DataBufferByte) raster.getDataBuffer();
            byte[] pixels = dataBuffer.getData();

            // Get the number of components per pixel
            int pixelStride = raster.getNumBands();
            int totalLength = pixels.length;

            // Process the data in a single pass
            int outIndex = 0;
            for (int i = 0; i < totalLength; i += pixelStride) {
                // Check alpha if it exists
                if (pixelStride == 4 && (pixels[i + 3] & 0xFF) != 0xFF) {
                    out[outIndex++] = Double.NaN;
                    continue;
                }

                // Get RGB values
                int r = pixels[i] & 0xFF;
                int g = pixels[i + 1] & 0xFF;
                int b = pixels[i + 2] & 0xFF;

                int c = (r << 16) | (g << 8) | b;
                out[outIndex++] = ((c & ~0xFF000000) - 0x00800000) * (1.0d / 256.0d);
            }
            return out;
        } finally {
            long duration = System.nanoTime() - startTime;
            PerformanceTracker.trackOperation("ParseTerrariumPngDSP parse", duration);
        }
    }
}
