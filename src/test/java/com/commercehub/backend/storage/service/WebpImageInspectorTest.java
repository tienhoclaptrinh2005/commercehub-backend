package com.commercehub.backend.storage.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebpImageInspectorTest {

    @Test
    void readsExtendedWebpDimensionsAndMetadataFlags() {
        byte[] extendedData = new byte[10];
        extendedData[0] = 0x08;
        write24(extendedData, 4, 1199);
        write24(extendedData, 7, 899);
        byte[] bytes = container(
                new Chunk("VP8X", extendedData),
                new Chunk("VP8 ", lossyData())
        );

        WebpImageInspector.ImageInfo info = WebpImageInspector.inspect(bytes);

        assertThat(info.width()).isEqualTo(1200);
        assertThat(info.height()).isEqualTo(900);
        assertThat(info.containsMetadata()).isTrue();
        assertThat(info.animated()).isFalse();
    }

    @Test
    void readsLossyWebpDimensions() {
        WebpImageInspector.ImageInfo info = WebpImageInspector.inspect(
                container(new Chunk("VP8 ", lossyData()))
        );

        assertThat(info.width()).isEqualTo(1200);
        assertThat(info.height()).isEqualTo(900);
    }

    @Test
    void readsLosslessWebpDimensions() {
        byte[] data = new byte[5];
        data[0] = 0x2F;
        long packedDimensions = 1199L | (899L << 14);
        write32(data, 1, packedDimensions);

        WebpImageInspector.ImageInfo info = WebpImageInspector.inspect(
                container(new Chunk("VP8L", data))
        );

        assertThat(info.width()).isEqualTo(1200);
        assertThat(info.height()).isEqualTo(900);
    }

    @Test
    void rejectsTruncatedContainers() {
        assertThatThrownBy(() -> WebpImageInspector.inspect("not-webp".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDataAppendedOutsideTheWebpContainer() {
        byte[] valid = container(new Chunk("VP8 ", lossyData()));
        byte[] withTrailingData = Arrays.copyOf(valid, valid.length + 4);

        assertThatThrownBy(() -> WebpImageInspector.inspect(withTrailingData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
    }

    private byte[] lossyData() {
        byte[] data = new byte[10];
        data[3] = (byte) 0x9D;
        data[4] = 0x01;
        data[5] = 0x2A;
        write16(data, 6, 1200);
        write16(data, 8, 900);
        return data;
    }

    private byte[] container(Chunk... chunks) {
        int chunksLength = 0;
        for (Chunk chunk : chunks) {
            chunksLength += 8 + chunk.data().length + (chunk.data().length & 1);
        }
        byte[] bytes = new byte[12 + chunksLength];
        writeText(bytes, 0, "RIFF");
        write32(bytes, 4, bytes.length - 8L);
        writeText(bytes, 8, "WEBP");

        int offset = 12;
        for (Chunk chunk : chunks) {
            writeText(bytes, offset, chunk.type());
            write32(bytes, offset + 4, chunk.data().length);
            System.arraycopy(chunk.data(), 0, bytes, offset + 8, chunk.data().length);
            offset += 8 + chunk.data().length + (chunk.data().length & 1);
        }
        return bytes;
    }

    private void writeText(byte[] target, int offset, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, encoded.length);
    }

    private void write16(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
    }

    private void write24(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
    }

    private void write32(byte[] target, int offset, long value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
        target[offset + 3] = (byte) (value >> 24);
    }

    private record Chunk(String type, byte[] data) {
    }
}
