package com.codexdesktop.ui.markdown;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders Markdown into plain JavaFX nodes.
 *
 * <p>Parsing is delegated to commonmark-java (a real CommonMark parser, not regex heuristics)
 * and the resulting AST is mapped onto {@link TextFlow} / {@link VBox} nodes. No WebView is
 * involved, so a long transcript stays cheap and consistent with the rest of the UI styling.
 *
 * <p>Supported: headings, paragraphs, bold, italic, inline code, fenced and indented code,
 * bullet and ordered lists, links, block quotes, thematic breaks.
 */
public final class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final Consumer<String> linkOpener;

    public MarkdownRenderer(Consumer<String> linkOpener) {
        this.linkOpener = linkOpener;
    }

    /** @return one node per top-level block; an empty list for blank input */
    public List<Node> render(String markdown) {
        List<Node> blocks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return blocks;
        }
        Document document = (Document) parser.parse(markdown);
        for (org.commonmark.node.Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            Node rendered = renderBlock(child, 0);
            if (rendered != null) {
                blocks.add(rendered);
            }
        }
        return blocks;
    }

    private Node renderBlock(org.commonmark.node.Node node, int indentLevel) {
        if (node instanceof Heading heading) {
            TextFlow flow = inlineFlow(heading);
            flow.getStyleClass().add("md-heading-" + Math.min(3, heading.getLevel()));
            return flow;
        }
        if (node instanceof Paragraph paragraph) {
            TextFlow flow = inlineFlow(paragraph);
            flow.getStyleClass().add("md-paragraph");
            return flow;
        }
        if (node instanceof FencedCodeBlock code) {
            return new CodeBlockView(code.getInfo(), code.getLiteral());
        }
        if (node instanceof IndentedCodeBlock code) {
            return new CodeBlockView("", code.getLiteral());
        }
        if (node instanceof BulletList list) {
            return renderList(list, indentLevel, false, 1);
        }
        if (node instanceof OrderedList list) {
            return renderList(list, indentLevel, true, list.getMarkerStartNumber() == null
                    ? 1 : list.getMarkerStartNumber());
        }
        if (node instanceof BlockQuote quote) {
            VBox box = new VBox(4);
            box.getStyleClass().add("md-quote");
            for (org.commonmark.node.Node child = quote.getFirstChild(); child != null; child = child.getNext()) {
                Node rendered = renderBlock(child, indentLevel);
                if (rendered != null) {
                    box.getChildren().add(rendered);
                }
            }
            return box;
        }
        if (node instanceof ThematicBreak) {
            Region line = new Region();
            line.getStyleClass().add("turn-divider");
            line.setMinHeight(1);
            line.setPrefHeight(1);
            return line;
        }
        // Anything else (HTML blocks, tables from unsupported extensions) degrades to text.
        String text = collectText(node);
        if (text.isBlank()) {
            return null;
        }
        TextFlow flow = new TextFlow(styledText(text, "text-primary"));
        flow.getStyleClass().add("md-paragraph");
        return flow;
    }

    private Node renderList(org.commonmark.node.Node list, int indentLevel, boolean ordered, int startNumber) {
        VBox box = new VBox(3);
        box.setPadding(new javafx.geometry.Insets(1, 0, 1, indentLevel == 0 ? 2 : 14));
        int index = startNumber;
        for (org.commonmark.node.Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) {
                continue;
            }
            String marker = ordered ? (index++) + "." : "\u2022";
            VBox itemContent = new VBox(3);
            for (org.commonmark.node.Node child = item.getFirstChild(); child != null; child = child.getNext()) {
                Node rendered = renderBlock(child, indentLevel + 1);
                if (rendered != null) {
                    itemContent.getChildren().add(rendered);
                }
            }
            Label markerLabel = new Label(marker);
            markerLabel.getStyleClass().add("text-muted");
            markerLabel.setMinWidth(ordered ? 18 : 12);
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(6, markerLabel, itemContent);
            javafx.scene.layout.HBox.setHgrow(itemContent, javafx.scene.layout.Priority.ALWAYS);
            box.getChildren().add(row);
        }
        return box;
    }

    private TextFlow inlineFlow(org.commonmark.node.Node parent) {
        TextFlow flow = new TextFlow();
        appendInline(flow, parent, false, false);
        return flow;
    }

    private void appendInline(TextFlow flow, org.commonmark.node.Node parent, boolean bold, boolean italic) {
        for (org.commonmark.node.Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof org.commonmark.node.Text text) {
                flow.getChildren().add(decorate(new Text(text.getLiteral()), bold, italic));
            } else if (node instanceof StrongEmphasis) {
                appendInline(flow, node, true, italic);
            } else if (node instanceof Emphasis) {
                appendInline(flow, node, bold, true);
            } else if (node instanceof Code code) {
                Text inline = new Text(code.getLiteral());
                inline.getStyleClass().add("md-inline-code");
                flow.getChildren().add(inline);
            } else if (node instanceof Link link) {
                String label = collectText(link);
                Text linkText = new Text(label.isBlank() ? link.getDestination() : label);
                linkText.getStyleClass().add("md-link");
                String destination = link.getDestination();
                linkText.setOnMouseClicked(event -> linkOpener.accept(destination));
                flow.getChildren().add(linkText);
            } else if (node instanceof Image image) {
                String alt = collectText(image);
                flow.getChildren().add(styledText("[image" + (alt.isBlank() ? "" : ": " + alt) + "]", "text-muted"));
            } else if (node instanceof SoftLineBreak) {
                flow.getChildren().add(new Text(" "));
            } else if (node instanceof HardLineBreak) {
                flow.getChildren().add(new Text("\n"));
            } else if (node instanceof HtmlInline html) {
                flow.getChildren().add(styledText(html.getLiteral(), "text-muted"));
            } else {
                appendInline(flow, node, bold, italic);
            }
        }
    }

    private static Text decorate(Text text, boolean bold, boolean italic) {
        text.getStyleClass().add("text-primary");
        StringBuilder style = new StringBuilder();
        if (bold) {
            style.append("-fx-font-weight: bold;");
        }
        if (italic) {
            style.append("-fx-font-style: italic;");
        }
        if (!style.isEmpty()) {
            text.setStyle(style.toString());
        }
        return text;
    }

    private static Text styledText(String value, String styleClass) {
        Text text = new Text(value);
        text.getStyleClass().add(styleClass);
        return text;
    }

    private static String collectText(org.commonmark.node.Node parent) {
        StringBuilder builder = new StringBuilder();
        collectText(parent, builder);
        return builder.toString();
    }

    private static void collectText(org.commonmark.node.Node parent, StringBuilder builder) {
        for (org.commonmark.node.Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof org.commonmark.node.Text text) {
                builder.append(text.getLiteral());
            } else if (node instanceof Code code) {
                builder.append(code.getLiteral());
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                builder.append(' ');
            } else {
                collectText(node, builder);
            }
        }
    }
}
