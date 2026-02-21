package com.ai.aigent.utils;

import com.ai.aigent.tools.PDFGenerationTool;
import com.itextpdf.text.pdf.BaseFont;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Objects;

public class PdfExportUtil {

    public static byte[] toPdf(String markdownText) {
        try {
            // 1. 预处理，去掉 <Think> 块
            String cleanedText = markdownText.replaceAll("(?si)<Think>.*?</Think>", "");
            // 2. Markdown → HTML
            MutableDataSet options = new MutableDataSet();
            Parser parser = Parser.builder(options).build();
            HtmlRenderer renderer = HtmlRenderer.builder(options).build();
            String body = renderer.render(parser.parse(cleanedText));

            // 3. 提取字体到临时文件
            InputStream fontStream = PdfExportUtil.class
                    .getClassLoader().getResourceAsStream("fonts/simhei.ttf");
            if (fontStream == null) {
                throw new RuntimeException("字体文件未找到，请确认放在 src/main/resources/fonts/ 下");
            }
            File tempFont = File.createTempFile("noto", ".ttf");
            tempFont.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tempFont)) {
                fontStream.transferTo(fos);
            }
              String html = """
                    <html>
                    <head>
                    <meta charset="UTF-8"/>
                    <style>
                    body {
                        font-family: SimHei;
                        font-size: 13px;
                        line-height: 1.8;
                        margin: 40px;
                    }
                    h1 { font-size: 22px; }
                    h2 { font-size: 18px; }
                    h3 { font-size: 15px; }
                    code { background: #f4f4f4; padding: 2px 4px; }
                    pre  { background: #f4f4f4; padding: 10px; }
                    table { border-collapse: collapse; width: 100%%; }
                    td, th { border: 1px solid #ccc; padding: 6px; }
                    </style>
                    </head>
                    <body>
                    """ + body + "</body></html>";

            // 5. HTML → PDF
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ITextRenderer renderers = new ITextRenderer();
            renderers.getFontResolver().addFont(
                    tempFont.getAbsolutePath(),
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED
            );

            renderers.setDocumentFromString(html);
            renderers.layout();
            renderers.createPDF(out);

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("PDF生成失败", e);
        }
    }
}
