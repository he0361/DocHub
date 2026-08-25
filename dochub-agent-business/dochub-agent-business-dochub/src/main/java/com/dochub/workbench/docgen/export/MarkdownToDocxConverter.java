package com.dochub.workbench.docgen.export;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BulletList;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown AST → XWPFDocument 转换器。
 *
 * <p>支持标题、段落、有序/无序列表、代码块、分割线、加粗/斜体、GFM 表格。
 * 中文字体需在 XWPFRun 的 rPr 上同时设置 East Asian 字体，否则 Word 中文会乱码。</p>
 */
@Component
public class MarkdownToDocxConverter {

    private static final String CN_FONT = "微软雅黑";
    private static final String CN_FONT_EAST_ASIA = "微软雅黑";
    private static final String CODE_FONT = "Consolas";

    private final Parser parser;

    public MarkdownToDocxConverter() {
        this.parser = Parser.builder().extensions(List.of(TablesExtension.create())).build();
    }

    public XWPFDocument convert(String markdown) {
        XWPFDocument document = new XWPFDocument();
        Node root = parser.parse(markdown == null ? "" : markdown);
        appendBlocks(root, document);
        return document;
    }

    private void appendBlocks(Node parent, XWPFDocument document) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                appendHeading(document, heading);
            }
            else if (node instanceof Paragraph paragraph) {
                appendParagraph(document, paragraph, "");
            }
            else if (node instanceof BulletList bulletList) {
                appendList(document, bulletList, "• ");
            }
            else if (node instanceof OrderedList orderedList) {
                appendOrderedList(document, orderedList);
            }
            else if (node instanceof FencedCodeBlock fencedCodeBlock) {
                appendCodeBlock(document, fencedCodeBlock.getLiteral());
            }
            else if (node instanceof IndentedCodeBlock indentedCodeBlock) {
                appendCodeBlock(document, indentedCodeBlock.getLiteral());
            }
            else if (node instanceof TableBlock tableBlock) {
                appendTable(document, tableBlock);
            }
            else if (node instanceof ThematicBreak) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText("────────────────────────────────");
                applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
            }
            else if (node instanceof Text text) {
                appendParagraph(document, text);
            }
        }
    }

    private void appendHeading(XWPFDocument document, Heading heading) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(160);
        paragraph.setSpacingAfter(120);
        int level = Math.max(1, Math.min(6, heading.getLevel()));
        int fontSize = switch (level) {
            case 1 -> 20;
            case 2 -> 17;
            case 3 -> 15;
            case 4 -> 14;
            case 5 -> 13;
            default -> 12;
        };
        if (level == 1) {
            paragraph.setAlignment(ParagraphAlignment.CENTER);
        }
        for (XWPFRun run : appendInlineRuns(heading, paragraph)) {
            run.setBold(true);
            run.setFontSize(fontSize);
            applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
        }
    }

    private void appendParagraph(XWPFDocument document, Paragraph paragraph, String prefix) {
        XWPFParagraph xwpfParagraph = document.createParagraph();
        xwpfParagraph.setSpacingAfter(80);
        if (!prefix.isEmpty()) {
            xwpfParagraph.setIndentationLeft(240);
            XWPFRun prefixRun = xwpfParagraph.createRun();
            prefixRun.setText(prefix);
            applyFont(prefixRun, CN_FONT, CN_FONT_EAST_ASIA);
        }
        appendInlineRuns(paragraph, xwpfParagraph);
    }

    private void appendParagraph(XWPFDocument document, Text text) {
        XWPFParagraph xwpfParagraph = document.createParagraph();
        xwpfParagraph.setSpacingAfter(80);
        XWPFRun run = xwpfParagraph.createRun();
        run.setText(text.getLiteral() == null ? "" : text.getLiteral());
        applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
    }

    private void appendList(XWPFDocument document, BulletList list, String bullet) {
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof ListItem listItem) {
                appendListItem(document, listItem, bullet, 0);
            }
        }
    }

    private void appendOrderedList(XWPFDocument document, OrderedList list) {
        int index = 1;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof ListItem listItem) {
                appendListItem(document, listItem, index + ". ", 0);
                index++;
            }
        }
    }

    private void appendListItem(XWPFDocument document, ListItem listItem, String marker, int indentLevel) {
        for (Node child = listItem.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Paragraph paragraph) {
                XWPFParagraph xwpfParagraph = document.createParagraph();
                xwpfParagraph.setSpacingAfter(60);
                xwpfParagraph.setIndentationLeft(240 * (indentLevel + 1));
                XWPFRun markerRun = xwpfParagraph.createRun();
                markerRun.setText(marker);
                applyFont(markerRun, CN_FONT, CN_FONT_EAST_ASIA);
                appendInlineRuns(paragraph, xwpfParagraph);
            }
            else if (child instanceof BulletList nestedBullet) {
                appendList(document, nestedBullet, "– ");
            }
            else if (child instanceof OrderedList nestedOrdered) {
                appendOrderedList(document, nestedOrdered);
            }
        }
    }

    private void appendCodeBlock(XWPFDocument document, String literal) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(80);
        paragraph.setIndentationLeft(120);
        String[] lines = (literal == null ? "" : literal).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            run.setText(lines[i]);
            run.setFontFamily(CODE_FONT);
            run.setFontSize(10);
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private void appendTable(XWPFDocument document, TableBlock tableBlock) {
        // gfm-tables AST 结构：TableBlock → (TableHead/TableBody) → TableRow → TableCell
        List<List<String>> rows = new ArrayList<>();
        for (Node part = tableBlock.getFirstChild(); part != null; part = part.getNext()) {
            if (!(part instanceof TableHead) && !(part instanceof TableBody)) {
                continue;
            }
            for (Node rowNode = part.getFirstChild(); rowNode != null; rowNode = rowNode.getNext()) {
                if (rowNode instanceof TableRow row) {
                    List<String> cells = new ArrayList<>();
                    for (Node cellNode = row.getFirstChild(); cellNode != null; cellNode = cellNode.getNext()) {
                        if (cellNode instanceof TableCell cell) {
                            cells.add(cellText(cell));
                        }
                    }
                    if (!cells.isEmpty()) {
                        rows.add(cells);
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        int columnCount = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = document.createTable(rows.size(), columnCount);
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow xwpfRow = table.getRow(r);
            List<String> row = rows.get(r);
            for (int c = 0; c < columnCount; c++) {
                XWPFTableCell cell = xwpfRow.getCell(c);
                if (cell == null) {
                    cell = xwpfRow.createCell();
                }
                String text = c < row.size() ? row.get(c) : "";
                XWPFParagraph cellParagraph = cell.getParagraphs().isEmpty()
                    ? cell.addParagraph() : cell.getParagraphs().get(0);
                XWPFRun run = cellParagraph.createRun();
                run.setText(text);
                applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
                if (r == 0) {
                    run.setBold(true);
                }
            }
        }
    }

    private String cellText(Node cell) {
        StringBuilder builder = new StringBuilder();
        appendInlineText(cell, builder);
        return builder.toString();
    }

    private void appendInlineText(Node node, StringBuilder builder) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text text) {
                builder.append(text.getLiteral() == null ? "" : text.getLiteral());
            }
            else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                builder.append(' ');
            }
            else {
                appendInlineText(child, builder);
            }
        }
    }

    /** 把容器内行内元素渲染为 XWPFRun 序列并返回，供调用方按需二次格式化。 */
    private List<XWPFRun> appendInlineRuns(Node container, XWPFParagraph paragraph) {
        List<XWPFRun> runs = new ArrayList<>();
        for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text text) {
                XWPFRun run = paragraph.createRun();
                run.setText(text.getLiteral() == null ? "" : text.getLiteral());
                applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
                runs.add(run);
            }
            else if (child instanceof StrongEmphasis) {
                XWPFRun run = paragraph.createRun();
                run.setBold(true);
                run.setText(plainText(child));
                applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
                runs.add(run);
            }
            else if (child instanceof Emphasis) {
                XWPFRun run = paragraph.createRun();
                run.setItalic(true);
                run.setText(plainText(child));
                applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
                runs.add(run);
            }
            else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                // 行内软/硬换行在 Word 段落内直接忽略，由段落边界承担换行
            }
            else {
                XWPFRun run = paragraph.createRun();
                run.setText(plainText(child));
                applyFont(run, CN_FONT, CN_FONT_EAST_ASIA);
                runs.add(run);
            }
        }
        return runs;
    }

    private String plainText(Node node) {
        StringBuilder builder = new StringBuilder();
        appendInlineText(node, builder);
        return builder.toString().trim();
    }

    private void applyFont(XWPFRun run, String asciiFont, String eastAsiaFont) {
        run.setFontFamily(asciiFont);
        CTRPr rpr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        List<CTFonts> existing = rpr.getRFontsList();
        CTFonts fonts = (existing == null || existing.isEmpty()) ? rpr.addNewRFonts() : existing.get(0);
        fonts.setEastAsia(eastAsiaFont);
        fonts.setAscii(asciiFont);
        fonts.setHAnsi(asciiFont);
    }
}
