package com.example.BES.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    void generateQrCode_returnsPngBytes() throws Exception {
        byte[] result = service.generateQrCode("https://example.com", 200, 200);

        assertThat(result).isNotEmpty();
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(result[0]).isEqualTo((byte) 0x89);
        assertThat(result[1]).isEqualTo((byte) 0x50);
    }

    @Test
    void generateQrCode_worksWithSmallDimensions() throws Exception {
        byte[] result = service.generateQrCode("hello", 100, 100);

        assertThat(result).isNotEmpty();
    }
}
