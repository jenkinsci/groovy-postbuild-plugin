/*
 * The MIT License
 *
 * Copyright (c) 2015 IKEDA Yasuyuki
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jenkinsci.plugins.badge.action.BadgeAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.util.Collections;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests requires Jenkins launched for each test methods.
 */
@WithJenkins
class GroovyPostbuildRecorderAnnotatedTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testDependencyToAnotherPlugin() throws Exception {
        // Test with script security plugin because it is already a dependency
        String script = "import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApprovalLink;"
                + "manager.addShortText((new ScriptApprovalLink()).getDisplayName());";

        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList()
                .add(new GroovyPostbuildRecorder(
                        new SecureGroovyScript(script, false, Collections.emptyList()), 2, false));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        BadgeAction action = b.getAction(BadgeAction.class);
        assertEquals("", action.getUrlName());
        assertEquals("In-process Script Approval", action.getText());
        assertNull(action.getIconFileName());
        assertNull(action.getLink());
    }
}
