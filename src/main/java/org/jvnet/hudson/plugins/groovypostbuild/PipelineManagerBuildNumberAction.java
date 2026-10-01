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

import hudson.model.InvisibleAction;
import hudson.model.Run;

/**
 * Records, on the {@link Run} a Pipeline step is actually executing on, which build number
 * {@code manager} in that Pipeline is currently redirected to via {@link
 * GroovyPostbuildRecorder.BadgeManager#setBuildNumber(int)}.
 *
 * <p>{@link WorkflowManager#getValue} builds a brand new {@code BadgeManager} every time a
 * script references {@code manager}, so without this the redirect chosen by one reference
 * would be lost by the next one (JENKINS-43012). Keeping the chosen build number as an
 * ordinary action on the run - the same mechanism {@code manager}'s badges themselves use -
 * lets it survive both later references within the run and a Jenkins restart, with no
 * static or in-memory cache to leak.
 */
public class PipelineManagerBuildNumberAction extends InvisibleAction {

    private final int buildNumber;

    public PipelineManagerBuildNumberAction(int buildNumber) {
        this.buildNumber = buildNumber;
    }

    public int getBuildNumber() {
        return buildNumber;
    }
}
