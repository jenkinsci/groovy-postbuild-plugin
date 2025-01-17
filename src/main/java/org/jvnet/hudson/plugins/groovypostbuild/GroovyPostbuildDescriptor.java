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
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
public class GroovyPostbuildDescriptor extends BuildStepDescriptor<Publisher> {

    /**
     * Constructs a {@link GroovyPostbuildDescriptor}.
     */
    public GroovyPostbuildDescriptor() {
        super(GroovyPostbuildRecorder.class);
    }

    /**
     * Gets the descriptor display name, used in the post step checkbox description.
     * @return the descriptor display name
     */
    @Override
    public final String getDisplayName() {
        return "Groovy Postbuild";
    }

    @Override
    public String getHelpFile() {
        return super.getHelpFile();
    }

    /**
     * Checks whether this descriptor is applicable.
     * @param clazz
     *            the class
     * @return true
     */
    @SuppressWarnings("unchecked")
    @Override
    public final boolean isApplicable(final Class<? extends AbstractProject> clazz) {
        return true;
    }

    /**
     * Check whether the configuring model is {@link MatrixProject}. Called from jelly.
     *
     * Note: Caller should pass it for the model is not bound to
     * {@link StaplerRequest2#findAncestorObject(Class)}
     * when called via hetero-list.
     *
     * @param it
     * @return true if the target model is {@link MatrixProject}
     */
    public boolean isMatrixProject(Object it) {
        return (it != null) && (it instanceof MatrixProject);
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void addAliases() {
        // migration from groovy-postbuild 2.3.1- to badge-plugin
        Run.XSTREAM2.addCompatibilityAlias(
                "org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildAction", BadgeAction.class);
        Run.XSTREAM2.addCompatibilityAlias(
                "org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildSummaryAction",
                GroovyPostbuildSummaryActionMigrator.class);
    }
}
