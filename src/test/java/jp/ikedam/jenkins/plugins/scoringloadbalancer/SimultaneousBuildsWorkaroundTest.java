/*
 * The MIT License
 *
 * Copyright (c) 2013 IKEDA Yasuyuki
 * Copyright (c) 2024 voruti
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
package jp.ikedam.jenkins.plugins.scoringloadbalancer;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.DescriptorImpl;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.DummySubTask;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TestingScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TriggerOtherProjectProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Test the optional workaround feature, which fixes scheduling for builds that are started at the same time.
 */
@WithJenkins
class SimultaneousBuildsWorkaroundTest {

    private static final int BUILD_TIMEOUT = 20;

    private JenkinsRule j;

    TestingScoringRule scoringRule;
    DescriptorImpl descriptor;
    Slave node0;

    FreeStyleProject p1;
    DummySubTask d1;
    FreeStyleProject p2;
    DummySubTask d2;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        scoringRule = new TestingScoringRule();
        descriptor = (DescriptorImpl) j.jenkins.getDescriptorOrDie(ScoringLoadBalancer.class);
        node0 = createOnlineSlave("node0", 4);
    }

    @Test
    @Disabled("Test is unstable, as Jenkins sometimes chooses the correct node without the workaround")
    void testTwoSimultaneousBuilds_runsOnWrongNodeWithoutWorkaround() throws Exception {
        // prepare:
        descriptor.configure(true, true, false, 500, scoringRule);

        FreeStyleBuild b2 = commonTwoSimultaneousBuildsTest();

        // assert:
        assertEquals(j.jenkins, b2.getBuiltOn()); // without the workaround, the build-in node is erroneously chosen

        assertEquals(j.jenkins, p2.getLastBuiltOn());
        assertEquals(j.jenkins, d2.getLastBuiltOn());
    }

    @Test
    void testTwoSimultaneousBuilds_okWithWorkaround() throws Exception {
        // prepare:
        descriptor.configure(true, true, true, 500, scoringRule);

        FreeStyleBuild b2 = commonTwoSimultaneousBuildsTest();

        // assert:
        assertEquals(node0, b2.getBuiltOn()); // with the workaround, the preferred node is chosen

        assertEquals(node0, p2.getLastBuiltOn());
        assertEquals(node0, d2.getLastBuiltOn());
    }

    private FreeStyleBuild commonTwoSimultaneousBuildsTest() throws Exception {
        // prepare:
        p1 = j.createFreeStyleProject();
        d1 = new DummySubTask("Bound task 1", p1, 10000);
        p1.addProperty(new TriggerOtherProjectProperty(d1));

        p2 = j.createFreeStyleProject();
        d2 = new DummySubTask("Bound task 2", p2, 10000);
        p2.addProperty(new TriggerOtherProjectProperty(d2));

        scoringRule.scoreMap.clear();
        scoringRule.scoreMap.put(j.jenkins, 5);
        scoringRule.scoreMap.put(node0, 10); // this node should always have the highest priority

        // act:
        p1.scheduleBuild2(0);
        p2.scheduleBuild2(0);

        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);

        // assert:
        FreeStyleBuild b1 = p1.getLastBuild();
        assertNotNull(b1);
        j.assertBuildStatusSuccess(b1);
        assertEquals(node0, b1.getBuiltOn());

        assertEquals(node0, p1.getLastBuiltOn());
        assertEquals(node0, d1.getLastBuiltOn());

        FreeStyleBuild b2 = p2.getLastBuild();
        assertNotNull(b2);
        j.assertBuildStatusSuccess(b2);
        return b2;
    }

    private DumbSlave createOnlineSlave(String label, int numExecutor) throws Exception {
        DumbSlave slave;
        synchronized (j.jenkins) {
            String nodeName = String.format("slave%d", j.jenkins.getNodes().size());
            slave = new DumbSlave(
                    nodeName,
                    new File(j.jenkins.getRootDir(), "agent-work-dirs/" + nodeName).getAbsolutePath(),
                    j.createComputerLauncher(null));
            slave.setLabelString(label);
            slave.setNumExecutors(numExecutor);
            slave.setRetentionStrategy(RetentionStrategy.NOOP);
            j.jenkins.addNode(slave);
        }

        j.waitOnline(slave);
        return slave;
    }
}
