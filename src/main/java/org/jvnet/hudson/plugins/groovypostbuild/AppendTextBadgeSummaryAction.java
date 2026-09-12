/*
 * The MIT License
 *
 * Copyright (c) 2026 Groovy Postbuild Plugin Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.groovypostbuild;

import com.jenkinsci.plugins.badge.action.BadgeSummaryAction;
import java.util.Objects;
import org.apache.commons.text.StringEscapeUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * A {@link BadgeSummaryAction} that restores the {@code appendText} methods badge 2.8 offered,
 * which badge 3.x dropped in favor of {@link BadgeSummaryAction#setText(String)}.
 *
 * <p>The three overloads below reproduce badge 2.8's behavior exactly, byte for byte, except that
 * they build on top of {@link BadgeSummaryAction#getText()} rather than a private field, since
 * badge 3.x exposes no other way to read back the text already set. {@code getText()} returns the
 * markup-formatter-translated value (or the raw value when blank), so appended text is layered onto
 * that translated value, same as it always was.
 *
 * <p>This class is deliberately not persisted under its own name: {@link #writeReplace()} substitutes
 * a plain {@link BadgeSummaryAction} at serialization time, so {@code build.xml} never records this
 * plugin-local class name. Without that, every build a script summary was written to would become
 * unreadable ({@code CannotResolveClassException}, and Jenkins silently drops the action) on any
 * installation without this exact class - a downgrade, or simply the day this deprecated shim is
 * removed, which is the entire point of a deprecation path. {@code rawIcon}/{@code rawText}/{@code
 * rawLink} exist because {@link #writeReplace()} must not use {@code getIcon()}/{@code getText()}/
 * {@code getLink()}: those getters return a transformed view (backwards-compatible icon name
 * rewriting, markup-formatter translation, and silently dropping a link that fails a pattern check,
 * respectively), not the value that was actually stored, and baking the transformed view into the
 * "raw" field of the replacement would corrupt the badge on every subsequent read.
 */
public class AppendTextBadgeSummaryAction extends BadgeSummaryAction {

    private transient String rawIcon;
    private transient String rawText;
    private transient String rawLink;

    public AppendTextBadgeSummaryAction(
            String id, String icon, String text, String cssClass, String style, String link, String target) {
        super(id, icon, text, cssClass, style, link, target);
        this.rawIcon = icon;
        this.rawText = text;
        this.rawLink = link;
    }

    @Override
    public void setIcon(String icon) {
        super.setIcon(icon);
        this.rawIcon = icon;
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        this.rawText = text;
    }

    @Override
    public void setLink(String link) {
        super.setLink(link);
        this.rawLink = link;
    }

    private Object writeReplace() {
        return new BadgeSummaryAction(getId(), rawIcon, rawText, getCssClass(), getStyle(), rawLink, getTarget());
    }

    @Whitelisted
    public void appendText(String text) {
        appendText(text, false);
    }

    @Whitelisted
    public void appendText(String text, boolean escapeHtml) {
        if (escapeHtml) {
            text = StringEscapeUtils.escapeHtml4(text);
        }
        setText(Objects.requireNonNullElse(getText(), "") + text);
    }

    @Whitelisted
    public void appendText(String text, boolean escapeHtml, boolean bold, boolean italic, String color) {
        String startTags = "";
        String closeTags = "";
        if (bold) {
            startTags += "<b>";
            closeTags += "</b>";
        }
        if (italic) {
            startTags += "<i>";
            closeTags += "</i>";
        }
        if (color != null) {
            startTags += "<font color=\"" + StringEscapeUtils.escapeHtml4(color) + "\">";
            closeTags += "</font>";
        }
        if (escapeHtml) {
            text = StringEscapeUtils.escapeHtml4(text);
        }
        setText(Objects.requireNonNullElse(getText(), "") + startTags + text + closeTags);
    }
}
