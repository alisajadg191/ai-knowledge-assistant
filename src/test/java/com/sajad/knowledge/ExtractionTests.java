package com.sajad.knowledge;

import com.sajad.knowledge.document.TextExtractor;
import com.sajad.knowledge.api.ApiException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionTests {
    final TextExtractor extractor=new TextExtractor();
    @Test void overlappingChunksPreserveTextAndOrdinals() {
        String text="connection pool investigation ".repeat(150).strip();
        var chunks=extractor.extract("notes.md",text.getBytes(StandardCharsets.UTF_8));
        assertTrue(chunks.size()>2);
        assertEquals(1,chunks.getFirst().ordinal());
        assertNull(chunks.getFirst().page());
        assertTrue(chunks.stream().allMatch(c->c.text().length()<=1200));
        String overlap=chunks.getFirst().text().substring(chunks.getFirst().text().length()-180);
        assertTrue(chunks.get(1).text().contains(overlap));
        assertEquals("notes.md",extractor.safeName("../../notes.md"));
    }
    @Test void rejectsEmptyUnsupportedInvalidEncodingAndOversizedText() {
        assertEquals("FILE_SIZE",assertThrows(ApiException.class,()->extractor.extract("a.txt",new byte[0])).code());
        assertEquals(415,assertThrows(ApiException.class,()->extractor.extract("a.exe",new byte[]{1})).status());
        assertEquals("UNREADABLE_FILE",assertThrows(ApiException.class,()->extractor.extract("a.txt",new byte[]{(byte)0xff})).code());
        assertEquals("NO_TEXT",assertThrows(ApiException.class,()->extractor.extract("a.txt","   ".getBytes())).code());
        assertEquals(413,assertThrows(ApiException.class,()->extractor.extract("a.txt","a".repeat(60001).getBytes())).status());
    }
    @Test void pdfRetainsPageNumbers() throws Exception {
        try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()) {
            for(String text:new String[]{"First page evidence", "Second page evidence"}) {
                var page=new PDPage();pdf.addPage(page);
                try(var content=new PDPageContentStream(pdf,page)) {
                    content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    content.newLineAtOffset(50,700);content.showText(text);content.endText();
                }
            }
            pdf.save(out);
            var chunks=extractor.extract("guide.pdf",out.toByteArray());
            assertEquals(2,chunks.size());assertEquals(2,chunks.get(1).page());
            assertTrue(chunks.get(1).text().contains("Second page evidence"));
        }
    }
    @Test void scannedPdfProducesUsefulError() throws Exception {
        try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());pdf.save(out);
            assertEquals("NO_TEXT",assertThrows(ApiException.class,()->extractor.extract("scan.pdf",out.toByteArray())).code());
        }
    }
}
