/*
 * The MIT License
 *
 * Copyright (c) 2013 IKEDA Yasuyuki
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

package jp.ikedam.jenkins.plugins.scoringloadbalancer.rules;

import static org.junit.Assert.*;

import hudson.model.Executor;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.labels.LabelExpression;
import java.util.concurrent.TimeUnit;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.DescriptorImpl;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.DummySubTask;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.ScoringLoadBalancerJenkinsRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TestingScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TriggerOtherProjectProperty;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.UnstableBuilder;

/**
 *
 */
public class BuildResultScoringRuleJenkinsTest {
    private static final int BUILD_TIMEOUT = 10;
    private static final int SLEEP_BEFORE_BUILD = 3;

    @Rule
    public ScoringLoadBalancerJenkinsRule j = new ScoringLoadBalancerJenkinsRule();

    TestingScoringRule testScoringRule;
    Slave node1;
    Slave node2;
    Slave node3;

    @Before
    public void setUp() throws Exception {
        testScoringRule = new TestingScoringRule();
        node1 = j.createOnlineSlave();
        node2 = j.createOnlineSlave();
        node3 = j.createOnlineSlave();
    }

    private void setScoringRule(ScoringRule scoringRule) {
        DescriptorImpl descriptor = (DescriptorImpl) j.jenkins.getDescriptorOrDie(ScoringLoadBalancer.class);
        descriptor.configure(true, true, scoringRule, testScoringRule);
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testScoreSuccess() throws Exception {
        setScoringRule(new BuildResultScoringRule(10, 10, -1, 2, -1, -2));

        FreeStyleProject p = j.createFreeStyleProject();

        // #1 master success
        // #2 node1 success
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #3 test
        testScoringRule.clear();
        p.setAssignedLabel(null);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(18, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(20, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node3));

        // test only depends on last result.
        // #4 master failure
        // #5 node1 success
        // #6 master success
        p.getBuildersList().add(new FailureBuilder());
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.getBuildersList().clear();
        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #7 test
        testScoringRule.clear();
        p.setAssignedLabel(null);
        p.getBuildersList().clear();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(20, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(18, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node3));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testScoreUnstable() throws Exception {
        setScoringRule(new BuildResultScoringRule(10, 10, -1, 2, -1, -2));

        FreeStyleProject p = j.createFreeStyleProject();

        // #1 master unstable
        // #2 node1 unstable
        p.getBuildersList().add(new UnstableBuilder());
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #3 test (node2)
        node3.toComputer().disconnect(null);
        testScoringRule.clear();
        p.getBuildersList().clear();
        p.setAssignedLabel(null);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-9, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-10, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
        node3.toComputer().connect(true);

        // test only depends on last result.
        // #4 master success
        // #5 node1 unstable
        // #6 master unstable
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.getBuildersList().add(new UnstableBuilder());
        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #7 test
        testScoringRule.clear();
        p.setAssignedLabel(null);
        p.getBuildersList().clear();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-10, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-9, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(14, testScoringRule.nodesScoreList.get(0).getScore(node2));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node3));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testScoreFailure() throws Exception {
        setScoringRule(new BuildResultScoringRule(10, 10, -1, 2, -1, -2));

        FreeStyleProject p = j.createFreeStyleProject();

        // #1 master unstable
        // #2 node1 unstable
        p.getBuildersList().add(new FailureBuilder());
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #3 test (node2)
        node3.toComputer().disconnect(null);
        testScoringRule.clear();
        p.getBuildersList().clear();
        p.setAssignedLabel(null);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        node3.toComputer().connect(true);

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-18, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-20, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));

        // test only depends on last result.
        // #4 master success
        // #5 node1 unstable
        // #6 master unstable
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.getBuildersList().add(new FailureBuilder());
        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #7 test
        testScoringRule.clear();
        p.setAssignedLabel(null);
        p.getBuildersList().clear();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-20, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-18, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(14, testScoringRule.nodesScoreList.get(0).getScore(node2));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node3));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testScoreAbort() throws Exception {
        setScoringRule(new BuildResultScoringRule(10, 10, -1, 2, -1, -2));

        FreeStyleProject p = j.createFreeStyleProject();

        // #1 master success
        // #2 node1 unstable
        // #3 node2 failure
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        p.getBuildersList().add(new UnstableBuilder());
        j.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.setAssignedLabel(LabelExpression.parseExpression(node2.getNodeName()));
        p.getBuildersList().add(new FailureBuilder());
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #4 test (master)
        testScoringRule.clear();
        p.setAssignedLabel(null);
        p.getBuildersList().clear();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(16, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-9, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(-20, testScoringRule.nodesScoreList.get(0).getScore(node2));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node3));

        // #4 master aborted
        // #5 node1 aborted
        // #6 node2 aborted
        // #7 node3 aborted
        // p.scheduleBuild2(0).cancel() seems not work...
        p.getBuildersList().add(new SleepBuilder(60 * 1000));

        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        p.scheduleBuild2(0);
        Thread.sleep(SLEEP_BEFORE_BUILD * 1000);
        for (Executor executor : j.jenkins.toComputer().getExecutors()) {
            if (executor.isBusy()) {
                executor.interrupt(Result.ABORTED);
            }
        }
        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);
        j.assertBuildStatus(Result.ABORTED, p.getLastBuild());

        assertEquals(1, node1.getNumExecutors());
        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        p.scheduleBuild2(0);
        Thread.sleep(SLEEP_BEFORE_BUILD * 1000);
        assertTrue(node1.toComputer().getExecutors().get(0).isBusy());
        node1.toComputer().getExecutors().get(0).interrupt(Result.ABORTED);
        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);
        j.assertBuildStatus(Result.ABORTED, p.getLastBuild());

        assertEquals(1, node2.getNumExecutors());
        p.setAssignedLabel(LabelExpression.parseExpression(node2.getNodeName()));
        p.scheduleBuild2(0);
        Thread.sleep(SLEEP_BEFORE_BUILD * 1000);
        assertTrue(node2.toComputer().getExecutors().get(0).isBusy());
        node2.toComputer().getExecutors().get(0).interrupt(Result.ABORTED);
        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);
        j.assertBuildStatus(Result.ABORTED, p.getLastBuild());

        assertEquals(1, node3.getNumExecutors());
        p.setAssignedLabel(LabelExpression.parseExpression(node3.getNodeName()));
        p.scheduleBuild2(0);
        Thread.sleep(SLEEP_BEFORE_BUILD * 1000);
        assertTrue(node3.toComputer().getExecutors().get(0).isBusy());
        node3.toComputer().getExecutors().get(0).interrupt(Result.ABORTED);
        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);
        j.assertBuildStatus(Result.ABORTED, p.getLastBuild());

        // #8 test
        testScoringRule.clear();
        p.setAssignedLabel(null);
        p.getBuildersList().clear();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(12, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-4, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(-10, testScoringRule.nodesScoreList.get(0).getScore(node2));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node3));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testNumOfBuilds() throws Exception {
        setScoringRule(new BuildResultScoringRule(3, 10, -1, 1, -1, -1));

        FreeStyleProject p = j.createFreeStyleProject();

        // #1 master success
        // #2 node1 success
        p.setAssignedLabel(LabelExpression.parseExpression("master"));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        p.setAssignedLabel(LabelExpression.parseExpression(node1.getNodeName()));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        // #3 node1 success
        testScoringRule.clear();
        p.setAssignedLabel(null);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(9, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));

        // #4 node1 success
        testScoringRule.clear();
        p.setAssignedLabel(null);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(8, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));

        // #5 node1 success
        // Run out of NumOfBuilds.
        testScoringRule.clear();
        p.setAssignedLabel(null);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
    }

    public void testSubTask() throws Exception {
        setScoringRule(new BuildResultScoringRule(10, 10, -1, 1, -1, -1));

        FreeStyleProject p = j.createFreeStyleProject();
        DummySubTask subtask = new DummySubTask("Subtask", p, 1000);

        p.addProperty(new TriggerOtherProjectProperty(subtask));

        j.assertBuildStatusSuccess(p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        int i = 0;
        for (i = 0; i < testScoringRule.calledWorkChunkList.size(); ++i) {
            if (subtask.equals(testScoringRule.calledWorkChunkList.get(i).get(0))) {
                break;
            }
        }
        assertTrue(i < testScoringRule.calledWorkChunkList.size());
        for (Node node : testScoringRule.nodesScoreList.get(i).getNodes()) {
            assertEquals(0, testScoringRule.nodesScoreList.get(i).getScore(node));
        }
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testDescriptor() {
        assertNotNull(j.jenkins.getDescriptorOrDie(BuildResultScoringRule.class));
    }
}
