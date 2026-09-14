package com.codexdesktop.ui.conversation;

import com.codexdesktop.ui.markdown.MarkdownRenderer;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/**
 * One assistant message, laid out as document text rather than a chat bubble.
 *
 * <p>While the answer streams in, the block holds a single {@link Text} node that is updated in
 * place — appending a token never rebuilds the scene graph. When the item completes, the buffered
 * text is parsed once and replaced with rendered Markdown.
 */
public final class AssistantMessageBlock extends VBox {

    private final MarkdownRenderer renderer;
    private final StringBuilder buffer = new StringBuilder();
    private final Text streamingText = new Text();
    private final TextFlow streamingFlow = new TextFlow(streamingText);
    private boolean finished;

    public AssistantMessageBlock(MarkdownRenderer renderer, String phase) {
        this.renderer = renderer;
        getStyleClass().add("assistant-block");
        setSpacing(8);
        streamingText.getStyleClass().add("commentary".equals(phase) ? "text-secondary" : "text-primary");
        streamingFlow.getStyleClass().add("md-paragraph");
        getChildren().add(streamingFlow);
    }

    /** Appends streamed text. Called at most once per UI frame with all deltas of that frame. */
    public void appendDelta(String delta) {
        if (finished || delta == null || delta.isEmpty()) {
            return;
        }
        buffer.append(delta);
        streamingText.setText(buffer.toString());
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /** Replaces the streaming text with rendered Markdown. Idempotent. */
    public void finish(String finalText) {
        if (finished) {
            return;
        }
        finished = true;
        String text = (finalText == null || finalText.isBlank()) ? buffer.toString() : finalText;
        buffer.setLength(0);
        buffer.append(text);

        List<Node> blocks = renderer.render(text);
        if (blocks.isEmpty()) {
            getChildren().clear();
            setManaged(false);
            setVisible(false);
            return;
        }
        getChildren().setAll(blocks);
    }

    public String text() {
        return buffer.toString();
    }
}
