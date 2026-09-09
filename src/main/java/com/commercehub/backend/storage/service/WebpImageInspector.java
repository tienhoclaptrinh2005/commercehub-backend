package com.commercehub.backend.storage.service;

import java.nio.charset.StandardCharsets;

final class WebpImageInspector {

    private WebpImageInspector() {
    }

    static ImageInfo inspect(byte[] bytes) {
        if (bytes == null || bytes.length < 12
                || !fourCc(bytes, 0).equals("RIFF")
                || !fourCc(bytes, 8).equals("WEBP")) {
            throw new IllegalArgumentException("Invalid WebP container");
        }

        long declaredLength = unsignedInt32(bytes, 4) + 8L;
        if (declaredLength != bytes.length) {
            throw new IllegalArgumentException("WebP container length does not match the object length");
        }

        Integer width = null;
        Integer height = null;
        boolean metadata = false;
        boolean animated = false;
        boolean imagePayload = false;
        int offset = 12;

        while (offset + 8 <= bytes.length) {
            String chunkType = fourCc(bytes, offset);
            long chunkLength = unsignedInt32(bytes, offset + 4);
            long dataOffset = offset + 8L;
            long dataEnd = dataOffset + chunkLength;
            if (dataEnd > bytes.length || dataEnd > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid WebP chunk length");
            }

            int data = (int) dataOffset;
            if ("VP8X".equals(chunkType)) {
                requireChunkLength(chunkLength, 10);
                int flags = unsigned(bytes[data]);
                metadata |= (flags & 0x0C) != 0;
                animated |= (flags & 0x02) != 0;
                width = littleEndian24(bytes, data + 4) + 1;
                height = littleEndian24(bytes, data + 7) + 1;
            } else if ("VP8 ".equals(chunkType)) {
                requireChunkLength(chunkLength, 10);
                if (unsigned(bytes[data + 3]) != 0x9D
                        || unsigned(bytes[data + 4]) != 0x01
                        || unsigned(bytes[data + 5]) != 0x2A) {
                    throw new IllegalArgumentException("Invalid lossy WebP frame");
                }
                int frameWidth = littleEndian16(bytes, data + 6) & 0x3FFF;
                int frameHeight = littleEndian16(bytes, data + 8) & 0x3FFF;
                requireMatchingDimensions(width, height, frameWidth, frameHeight);
                width = frameWidth;
                height = frameHeight;
                imagePayload = true;
            } else if ("VP8L".equals(chunkType)) {
                requireChunkLength(chunkLength, 5);
                if (unsigned(bytes[data]) != 0x2F) {
                    throw new IllegalArgumentException("Invalid lossless WebP frame");
                }
                long dimensions = unsignedInt32(bytes, data + 1);
                int frameWidth = (int) (dimensions & 0x3FFF) + 1;
                int frameHeight = (int) ((dimensions >> 14) & 0x3FFF) + 1;
                requireMatchingDimensions(width, height, frameWidth, frameHeight);
                width = frameWidth;
                height = frameHeight;
                imagePayload = true;
            } else if ("EXIF".equals(chunkType) || "XMP ".equals(chunkType)) {
                metadata = true;
            } else if ("ANIM".equals(chunkType) || "ANMF".equals(chunkType)) {
                animated = true;
            }

            long paddedEnd = dataEnd + (chunkLength & 1L);
            if (paddedEnd > bytes.length) {
                throw new IllegalArgumentException("Invalid WebP chunk padding");
            }
            offset = (int) paddedEnd;
        }

        if (!imagePayload || width == null || height == null || width < 1 || height < 1) {
            throw new IllegalArgumentException("WebP dimensions are missing");
        }
        return new ImageInfo(width, height, metadata, animated);
    }

    private static void requireChunkLength(long actual, long minimum) {
        if (actual < minimum) {
            throw new IllegalArgumentException("WebP chunk is too short");
        }
    }

    private static void requireMatchingDimensions(
            Integer canvasWidth,
            Integer canvasHeight,
            int frameWidth,
            int frameHeight
    ) {
        if ((canvasWidth != null && canvasWidth != frameWidth)
                || (canvasHeight != null && canvasHeight != frameHeight)) {
            throw new IllegalArgumentException("WebP canvas and frame dimensions do not match");
        }
    }

    private static String fourCc(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            return "";
        }
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int littleEndian16(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8);
    }

    private static int littleEndian24(byte[] bytes, int offset) {
        return unsigned(bytes[offset])
                | (unsigned(bytes[offset + 1]) << 8)
                | (unsigned(bytes[offset + 2]) << 16);
    }

    private static long unsignedInt32(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(
                unsigned(bytes[offset])
                        | (unsigned(bytes[offset + 1]) << 8)
                        | (unsigned(bytes[offset + 2]) << 16)
                        | (unsigned(bytes[offset + 3]) << 24)
        );
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    record ImageInfo(int width, int height, boolean containsMetadata, boolean animated) {
    }
}
