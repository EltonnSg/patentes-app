package cl.patentes.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Escribe un .xlsx mínimo con una hoja, estilos simples e imágenes JPEG incrustadas. Sin dependencias de Android. */
public final class XlsxWriter {
    private XlsxWriter() {}

    public static final int ST_NORMAL = 0, ST_HEADER = 1, ST_RED = 2, ST_GREEN = 3, ST_ORANGE = 4;

    public static final class Cell {
        public final Object value;   // String, Number o null
        public final int style;
        public Cell(Object value, int style) { this.value = value; this.style = style; }
    }

    public static final class Image {
        public final int col;        // columna (desde 0)
        public final byte[] jpeg;
        public final int widthPx, heightPx;
        public Image(int col, byte[] jpeg, int widthPx, int heightPx) {
            this.col = col; this.jpeg = jpeg; this.widthPx = widthPx; this.heightPx = heightPx;
        }
    }

    public static final class Row {
        public final List<Cell> cells = new ArrayList<>();
        public final List<Image> images = new ArrayList<>();
        public double heightPt = 50;
    }

    private static final String NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default:
                    if (c >= 0x20 || c == '\n' || c == '\t' || c == '\r') b.append(c);
            }
        }
        return b.toString();
    }

    static String colName(int idx) {
        StringBuilder s = new StringBuilder();
        int n = idx + 1;
        while (n > 0) { int r = (n - 1) % 26; s.insert(0, (char) ('A' + r)); n = (n - 1) / 26; }
        return s.toString();
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        put(z, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    /**
     * @param header  textos del encabezado (fila 1)
     * @param widths  ancho de cada columna en caracteres
     * @param rows    filas de datos (desde la fila 2)
     */
    public static void write(OutputStream os, String sheetName, String[] header, double[] widths, List<Row> rows) throws IOException {
        List<byte[]> media = new ArrayList<>();
        StringBuilder drawing = new StringBuilder();
        StringBuilder drawingRels = new StringBuilder();
        int picId = 1;

        StringBuilder sheet = new StringBuilder();
        sheet.append(XML).append("<worksheet xmlns=\"").append(NS_MAIN).append("\" xmlns:r=\"").append(NS_REL).append("\">");
        sheet.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        sheet.append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        sheet.append("<cols>");
        for (int i = 0; i < widths.length; i++) {
            sheet.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        }
        sheet.append("</cols><sheetData>");

        sheet.append("<row r=\"1\" ht=\"32\" customHeight=\"1\">");
        for (int c = 0; c < header.length; c++) {
            sheet.append("<c r=\"").append(colName(c)).append("1\" s=\"").append(ST_HEADER).append("\" t=\"inlineStr\"><is><t>")
                    .append(esc(header[c])).append("</t></is></c>");
        }
        sheet.append("</row>");

        drawing.append(XML).append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" ")
                .append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"").append(NS_REL).append("\">");
        drawingRels.append(XML).append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");

        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int rn = r + 2;
            sheet.append("<row r=\"").append(rn).append("\" ht=\"").append(row.heightPt).append("\" customHeight=\"1\">");
            for (int c = 0; c < row.cells.size(); c++) {
                Cell cell = row.cells.get(c);
                String ref = colName(c) + rn;
                if (cell.value == null) {
                    sheet.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style).append("\"/>");
                } else if (cell.value instanceof Number) {
                    sheet.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style).append("\"><v>")
                            .append(cell.value.toString()).append("</v></c>");
                } else {
                    sheet.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(esc(cell.value.toString())).append("</t></is></c>");
                }
            }
            sheet.append("</row>");

            for (Image img : row.images) {
                media.add(img.jpeg);
                int n = media.size();
                long cx = img.widthPx * 9525L, cy = img.heightPx * 9525L;
                drawing.append("<xdr:oneCellAnchor><xdr:from><xdr:col>").append(img.col).append("</xdr:col><xdr:colOff>47625</xdr:colOff><xdr:row>")
                        .append(rn - 1).append("</xdr:row><xdr:rowOff>47625</xdr:rowOff></xdr:from><xdr:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
                        .append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"").append(picId + 1).append("\" name=\"Foto ").append(picId).append("\"/>")
                        .append("<xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
                        .append("<xdr:blipFill><a:blip r:embed=\"rId").append(n).append("\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                        .append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
                        .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>");
                drawingRels.append("<Relationship Id=\"rId").append(n).append("\" Type=\"").append(NS_REL)
                        .append("/image\" Target=\"../media/image").append(n).append(".jpeg\"/>");
                picId++;
            }
        }
        sheet.append("</sheetData>");
        boolean hasDrawing = !media.isEmpty();
        if (hasDrawing) sheet.append("<drawing r:id=\"rId1\"/>");
        sheet.append("</worksheet>");
        drawing.append("</xdr:wsDr>");
        drawingRels.append("</Relationships>");

        String styles = XML + "<styleSheet xmlns=\"" + NS_MAIN + "\">"
                + "<fonts count=\"5\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFB3261E\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FF1D7A46\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFA35B00\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF3F3F3\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"5\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"4\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";

        StringBuilder ct = new StringBuilder(XML);
        ct.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        if (hasDrawing) ct.append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
        ct.append("</Types>");

        String rootRels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";
        String workbook = XML + "<workbook xmlns=\"" + NS_MAIN + "\" xmlns:r=\"" + NS_REL + "\"><sheets><sheet name=\"" + esc(sheetName)
                + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
        String wbRels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"" + NS_REL + "/styles\" Target=\"styles.xml\"/></Relationships>";
        String sheetRels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/drawing\" Target=\"../drawings/drawing1.xml\"/></Relationships>";

        ZipOutputStream z = new ZipOutputStream(os);
        put(z, "[Content_Types].xml", ct.toString());
        put(z, "_rels/.rels", rootRels);
        put(z, "xl/workbook.xml", workbook);
        put(z, "xl/_rels/workbook.xml.rels", wbRels);
        put(z, "xl/styles.xml", styles);
        put(z, "xl/worksheets/sheet1.xml", sheet.toString());
        if (hasDrawing) {
            put(z, "xl/worksheets/_rels/sheet1.xml.rels", sheetRels);
            put(z, "xl/drawings/drawing1.xml", drawing.toString());
            put(z, "xl/drawings/_rels/drawing1.xml.rels", drawingRels.toString());
            for (int i = 0; i < media.size(); i++) put(z, "xl/media/image" + (i + 1) + ".jpeg", media.get(i));
        }
        z.finish();
        z.flush();
    }
}
