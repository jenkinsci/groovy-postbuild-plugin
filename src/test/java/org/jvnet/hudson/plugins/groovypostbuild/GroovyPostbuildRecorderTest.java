/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
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

import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;

import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.scripts.ClasspathEntry;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.UnstableBuilder;

public class GroovyPostbuildRecorderTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    private static final String TEXT_ON_FAILED = "Groovy";
    
    private static final String SCRIPT_FOR_MATRIX = StringUtils.join(new String[]{
            "import hudson.matrix.MatrixBuild;",
            "import hudson.matrix.MatrixRun;",
            "if (manager.buildIsA(MatrixBuild.class)) {",
            "  // codes for matrix parents.",
            "  manager.addShortText(\"parent\");",
            "} else if(manager.buildIsA(MatrixRun)) {",
            "  // codes for matrix children.",
            "  manager.addShortText(manager.getEnvVariable(\"axis1\"));",
            "} else {",
            "  // unexpected case.",
            "  manager.buildFailure();",
            "}"
    }, '\n');

    @Test
    public void testMatrixProjectWithParent() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList().add(new GroovyPostbuildRecorder(new SecureGroovyScript(SCRIPT_FOR_MATRIX, true, Collections.<ClasspathEntry>emptyList()), 2, true));
        
        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        
        assertEquals("parent", b.getAction(GroovyPostbuildAction.class).getText());
        assertEquals("value1", b.getRun(new Combination(axisList, "value1")).getAction(GroovyPostbuildAction.class).getText());
        assertEquals("value2", b.getRun(new Combination(axisList, "value2")).getAction(GroovyPostbuildAction.class).getText());
    }
    
    @Test
    public void testMatrixProjectWithoutParent() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList().add(new GroovyPostbuildRecorder(new SecureGroovyScript(SCRIPT_FOR_MATRIX, true, Collections.<ClasspathEntry>emptyList()), 2, false));
        
        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        
        assertNull(b.getAction(GroovyPostbuildAction.class));
        assertEquals("value1", b.getRun(new Combination(axisList, "value1")).getAction(GroovyPostbuildAction.class).getText());
        assertEquals("value2", b.getRun(new Combination(axisList, "value2")).getAction(GroovyPostbuildAction.class).getText());
    }
    
    /**
     * behavior = any
     * build succeeds
     * script succeeds
     * -> build succeeds
     * @throws Exception
     */
    @Test
    public void testBehaviorNotAffectWithSucceedingBuildSucceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior: behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();
            
            p.getPublishersList().add(new GroovyPostbuildRecorder(
                    new SecureGroovyScript(
                            "manager.addShortText('testing');",
                            true,
                            Collections.<ClasspathEntry>emptyList()
                    ),
                    behavior,   // behavior
                    false       // runForMatrixParent
            ));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.SUCCESS, b);
            assertEquals("testing", b.getAction(GroovyPostbuildAction.class).getText());
        }
    }
    
    /**
     * behavior = any
     * build unstable
     * script succeeds
     * -> build unstable
     * @throws Exception
     */
    @Test
    public void testBehaviorNotAffectWithUnstableBuildSuceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior: behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();
            
            p.getBuildersList().add(new UnstableBuilder());
            
            p.getPublishersList().add(new GroovyPostbuildRecorder(
                    new SecureGroovyScript(
                            "manager.addShortText('testing');",
                            true,
                            Collections.<ClasspathEntry>emptyList()
                    ),
                    behavior,   // behavior
                    false       // runForMatrixParent
            ));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.UNSTABLE, b);
            assertEquals("testing", b.getAction(GroovyPostbuildAction.class).getText());
        }
    }
    
    /**
     * behavior = any
     * build failed
     * script succeeds
     * -> build failed
     * @throws Exception
     */
    @Test
    public void testBehaviorNotAffectWithFailingBuildSuceedingScript() throws Exception {
        List<Integer> behaviors = Arrays.asList(0, 1, 2);
        for (int behavior: behaviors) {
            FreeStyleProject p = j.createFreeStyleProject();
            
            p.getBuildersList().add(new FailureBuilder());
            
            p.getPublishersList().add(new GroovyPostbuildRecorder(
                    new SecureGroovyScript(
                            "manager.addShortText('testing');",
                            true,
                            Collections.<ClasspathEntry>emptyList()
                    ),
                    behavior,   // behavior
                    false       // runForMatrixParent
            ));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.FAILURE, b);
            assertEquals("testing", b.getAction(GroovyPostbuildAction.class).getText());
        }
    }
    
    /**
     * behavior = DoNothing(0)
     * build succeeds
     * script failed
     * -> build succeeds
     * @throws Exception
     */
    @Test
    public void testBehaviorDoNothingWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                0,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    
    /**
     * behavior = DoNothing(0)
     * build unstable
     * script failed
     * -> build unstable
     * @throws Exception
     */
    @Test
    public void testBehaviorDoNothingWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(new UnstableBuilder());
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                0,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    /**
     * behavior = DoNothing(0)
     * build failed
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    public void testBehaviorDoNothingWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(new FailureBuilder());
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                0,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    /**
     * behavior = Mark build as unstable(1)
     * build succeeds
     * script failed
     * -> build unstable
     * @throws Exception
     */
    @Test
    public void testBehaviorMarkUnstableWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                1,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    
    /**
     * behavior = Mark build as unstable(1)
     * build unstable
     * script failed
     * -> build unstable
     * @throws Exception
     */
    @Test
    public void testBehaviorMarkUnstableWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(new UnstableBuilder());
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                1,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    /**
     * behavior = Mark build as unstable(1)
     * build failed
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    public void testBehaviorMarkUnstableWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(new FailureBuilder());
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                1,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    /**
     * behavior = Mark build as failed (2)
     * build succeeds
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    public void testBehaviorMarkFailedWithSucceedingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                2,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    
    /**
     * behavior = Mark build as failed (2)
     * build unstable
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    public void testBehaviorMarkFailedWithUnstableBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(new UnstableBuilder());
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                2,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }
    
    /**
     * behavior = Mark build as failed (2)
     * build failed
     * script failed
     * -> build failed
     * @throws Exception
     */
    @Test
    public void testBehaviorMarkFailedWithFailingBuildFailingScript() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(new FailureBuilder());
        
        p.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "blahblahblah",
                        true,
                        Collections.<ClasspathEntry>emptyList()
                ),
                2,      // behavior
                false   // runForMatrixParent
        ));
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, b);
        assertEquals(TEXT_ON_FAILED, b.getAction(GroovyPostbuildAction.class).getText());
    }

    @Test
    public void classPathApproval() throws Exception {
        // Authorized to manage item but not RUN_SCRIPT - otherwise the CP would be approved by saving it
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy();
        gmas.add(Jenkins.READ, "devel");
        for (Permission p : Item.PERMISSIONS.getPermissions()) {
            gmas.add(p, "devel");
        }
        j.jenkins.setAuthorizationStrategy(gmas);

        // Directory class path
        FreeStyleProject dirCP = j.createFreeStyleProject();
        String directory = tmp.newFolder().getAbsolutePath();
        dirCP.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "manager.addShortText('testing')",
                        false,
                        Collections.singletonList(new ClasspathEntry(directory))
                ),0, false
        ));

        ScriptApproval sa = ScriptApproval.get();
        assertThat(sa.getApprovedClasspathEntries(), emptyIterable());
        assertThat(sa.getPendingClasspathEntries(), emptyIterable());

        // File class path
        FreeStyleBuild b = dirCP.scheduleBuild2(0).get();
        j.assertLogContains("UnapprovedClasspathException", b);
        j.assertLogContains("classpath entry file:" + directory + "/ is a class directory, which are not allowed", b);
        assertThat(sa.getApprovedClasspathEntries(), emptyIterable());
        assertThat(sa.getPendingClasspathEntries(), emptyIterable());

        FreeStyleProject fileCP = j.createFreeStyleProject();
        File file = tmp.newFile("script.groovy");
        FileUtils.write(file, "def foo() { /*code here*/ }");
        fileCP.getPublishersList().add(new GroovyPostbuildRecorder(
                new SecureGroovyScript(
                        "foo()",
                        false,
                        Collections.singletonList(new ClasspathEntry(file.getAbsolutePath()))
                ),0,false
        ));

        assertThat(sa.getApprovedClasspathEntries(), emptyIterable());
        // Approval request created when configured
        assertThat(sa.getPendingClasspathEntries().get(0).getURL(), equalTo(file.toURI().toURL()));

        b = fileCP.scheduleBuild2(0).get();
        j.assertLogContains("UnapprovedClasspathException", b);
        j.assertLogContains("classpath entry file:" + file, b);
        j.assertLogContains(" not yet approved for use", b);
        assertThat(sa.getApprovedClasspathEntries(), emptyIterable());

        sa.approveClasspathEntry(sa.getPendingClasspathEntries().get(0).getHash());
        assertThat(sa.getPendingClasspathEntries(), emptyIterable());

        b = fileCP.scheduleBuild2(0).get();
        j.assertLogNotContains("UnapprovedClasspathException", b);
    }
}
