/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Serban Iordache
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

import hudson.model.BuildBadgeAction;

/**
 * Left for backward binary compatibilities.
 *
 * This doesn't provide any actual features.
 *
 * @deprecated use {@link BadgeAction} instead.
 */
@Deprecated
public class GroovyPostbuildAction implements BuildBadgeAction {
    private GroovyPostbuildAction() {}

    /* Action methods */
    public String getUrlName() { return ""; }
    public String getDisplayName() { return ""; }
    public String getIconFileName() { return null; }

    public boolean isTextOnly() { return false; }
    public String getIconPath() { return null; }
    public String getText() { return ""; }
    public String getColor() { return "#000000"; }
    public String getBackground() { return "#FFFF00"; }
    public String getBorder() { return "1px"; }
    public String getBorderColor() { return "#C0C000"; }
    public String getLink() { return null; }
}
