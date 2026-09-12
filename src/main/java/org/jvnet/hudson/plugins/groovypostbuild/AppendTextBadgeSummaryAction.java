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
 * <p>{@link AppendTextBadgeSummaryActionConverter} substitutes a plain {@link BadgeSummaryAction}
 * for this class when {@code Run.XSTREAM2} marshals it, so the fields written to {@code build.xml}
 * are the plain badge action's, not this class's own extra state. The outer XML element is still
 * tagged with this class's fully qualified name - that is how XStream remembers this is what
 * actually ran when the build happened - but it carries a {@code resolves-to} attribute pointing at
 * {@code BadgeSummaryAction}. XStream's {@code HierarchicalStreams.readClassType()} consults
 * {@code resolves-to} before ever trying to resolve the element name to a real class, so the
 * element stays readable even after this class is gone: on a downgrade to a controller that never
 * had it, or the day this deprecated shim is removed, which is the entire point of a deprecation
 * path. Verified empirically, not assumed: renaming the outer tag to a class that exists nowhere on
 * the classpath still deserializes correctly, purely from {@code resolves-to}.
 *
 * <p>This substitution is deliberately an XStream {@link com.thoughtworks.xstream.converters.Converter}
 * rather than a {@code writeReplace()} method: {@code writeReplace()} is honored by any Java
 * serialization, not just {@code Run.XSTREAM2}, and Pipeline's CPS interpreter persists a running
 * program's local variables with plain {@code ObjectOutputStream} across every durability
 * checkpoint. A {@code writeReplace()}-based shim would come back from that round trip as a plain
 * {@code BadgeSummaryAction} too, silently losing {@code appendText} the next time a script called
 * it after a controller restart. See {@link AppendTextBadgeSummaryActionConverter} for the detail.
 *
 * <p>{@code rawIcon}/{@code rawText}/{@code rawLink} shadow the values actually passed to the
 * constructor and to {@link #setIcon}/{@link #setText}/{@link #setLink}, because {@link
 * #toPlainBadgeSummaryAction()} must not read them back through {@code getIcon()}/{@code
 * getText()}/{@code getLink()}: those getters return a transformed view (backwards-compatible icon
 * name rewriting, markup-formatter translation, and silently dropping a link that fails a pattern
 * check, respectively), not the value that was actually stored, and baking the transformed view
 * into the "raw" field of the replacement would corrupt the badge on every subsequent read. These
 * fields do not need to be {@code transient} and there is no {@code readResolve()}: nothing about
 * this class's own state is ever written to XML in the first place - the converter substitutes a
 * plain {@code BadgeSummaryAction}, which does not have these fields, before any of this class's
 * own fields would be walked. The one case that does not round-trip is a shim element authored or
 * migrated some other way - with badge's own fields but none of these three - since the
 * superclass's real values are then reachable only through the same transforming getters this class
 * must not launder as "raw"; deliberately, that leaves the shadow fields null rather than
 * fabricating a value, so a save before the next {@code appendText}/{@code setText}/{@code
 * setIcon}/{@code setLink} call on such an object would persist an empty badge instead of silently
 * corrupting its icon, text, or link.
 */
public class AppendTextBadgeSummaryAction extends BadgeSummaryAction {

    private String rawIcon;
    private String rawText;
    private String rawLink;

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

    /**
     * Used by {@link AppendTextBadgeSummaryActionConverter} to build the object it substitutes for
     * this one when {@code Run.XSTREAM2} marshals it. Package-private: nothing outside the
     * converter needs it, and the raw* fields it reads stay encapsulated.
     */
    BadgeSummaryAction toPlainBadgeSummaryAction() {
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
        // NOTE: closeTags is built in the SAME order as startTags, not reversed, so the tags are
        // not properly nested (e.g. "<b><i><font ...>text</b></i></font>" for bold+italic+color).
        // That is badge 2.8's actual behavior, reproduced deliberately for compatibility - do not
        // "fix" it, since that would silently change what existing users' summaries render as.
        setText(Objects.requireNonNullElse(getText(), "") + startTags + text + closeTags);
    }
}
