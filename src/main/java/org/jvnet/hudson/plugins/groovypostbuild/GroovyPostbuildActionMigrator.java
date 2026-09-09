/*
 * The MIT License
 *
 * Copyright (c) 2018 IKEDA Yasuyuki
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

import com.jenkinsci.plugins.badge.action.BadgeAction;

/**
 * Converts GroovyPostbuildAction to {@link BadgeAction}
 *
 */
/*package*/ class GroovyPostbuildActionMigrator {
    private transient String iconPath;
    private transient String text;
    private transient String color;
    private transient String background;
    private transient String border;
    private transient String borderColor;
    private transient String link;

    protected BadgeAction readResolve() {
        String style = "";
        if (border != null) {
            style += "border: " + border + " solid " + (borderColor != null ? borderColor : "") + ";";
        }
        if (background != null) {
            style += "background: " + background + ";";
        }
        if (color != null) {
            if (color.startsWith("jenkins-!-color")) {
                style += "color: var(--" + color.replaceFirst("jenkins-!-color-", "") + ");";
            } else if (color.startsWith("jenkins-!-")) {
                style += "color: var(--" + color.replaceFirst("jenkins-!-", "") + ");";
            } else {
                style += "color: " + color + ";";
            }
        }

        return new BadgeAction(null, iconPath, text, null, style, link, null);
    }
}
