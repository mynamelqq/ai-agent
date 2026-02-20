package com.ai.aigent.utils;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;

public class PdfExportUtil {

    public static byte[] toPdf(String text) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            Document document = new Document();
            PdfWriter.getInstance(document, out);

            document.open();

            Font font = new Font(Font.HELVETICA, 12);
            Paragraph paragraph = new Paragraph(text, font);
            document.add(paragraph);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("PDF生成失败", e);
        }
    }
}
