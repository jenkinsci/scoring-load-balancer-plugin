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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.labels.LabelExpression;
import hudson.model.listeners.RunListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.DescriptorImpl;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.DummySubTask;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TestingScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TriggerOtherProjectProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 *
 */
@WithJenkins
class NodeLoadScoringRuleJenkinsTest {
    private static int BUILD_TIMEOUT = 10;

    private static final Lock buildStartLock = new ReentrantLock();
    private static final Condition buildStartCondition = buildStartLock.newCondition();

    private JenkinsRule j;

    TestingScoringRule testScoringRule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        this.j = j;
        testScoringRule = new TestingScoringRule();
    }

    @Test
    void testIdleScore() throws Exception {
        DumbSlave node1 = createOnlineSlave("node", 3);
        DumbSlave node2 = createOnlineSlave("node", 2);

        setScoringRule(new NodeLoadScoringRule(10, 3, 0));

        FreeStyleProject testingProject = j.createFreeStyleProject();
        FreeStyleProject node1Project = j.createFreeStyleProject();
        node1Project.setAssignedNode(node1);
        node1Project.getBuildersList().add(new SleepBuilder(60 * 1000));
        node1Project.setConcurrentBuild(true);

        FreeStyleProject node2Project = j.createFreeStyleProject();
        node2Project.setAssignedNode(node2);
        node2Project.getBuildersList().add(new SleepBuilder(60 * 1000));
        node2Project.setConcurrentBuild(true);

        assertEquals(3, node1.toComputer().getExecutors().size());
        assertEquals(2, node2.toComputer().getExecutors().size());

        // node1 idle 3
        // node2 idle 2
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(90, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node2));

        // build on node1
        startBuild(node1Project, BUILD_TIMEOUT);

        // node1 idle 2
        // node2 idle 2
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node2));

        // build on node2
        startBuild(node2Project, BUILD_TIMEOUT);

        // node1 idle 2
        // node2 idle 1
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(30, testScoringRule.nodesScoreList.get(0).getScore(node2));

        for (Executor e : node1.toComputer().getExecutors()) {
            if (e.isBusy()) {
                e.interrupt(Result.ABORTED);
            }
        }
        for (Executor e : node2.toComputer().getExecutors()) {
            if (e.isBusy()) {
                e.interrupt(Result.ABORTED);
            }
        }

        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);

        // node1 idle 3
        // node2 idle 2
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(90, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node2));
    }

    @Test
    void testIdleScoreForTaskset() throws Exception {
        DumbSlave node1 = createOnlineSlave("node", 4);
        setScoringRule(new NodeLoadScoringRule(5, 3, 0));

        FreeStyleProject testingProject = j.createFreeStyleProject();
        testingProject.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnSameNode = new DummySubTask("Task on same node", testingProject, 1000);
        taskOnSameNode.setSameNodeConstraint(testingProject);
        DummySubTask taskOnAnotherNode1 = new DummySubTask("Task on another node 1", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnAnotherNode2 = new DummySubTask("Task on another node 2", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        testingProject.addProperty(
                new TriggerOtherProjectProperty(taskOnSameNode, taskOnAnotherNode1, taskOnAnotherNode2));

        testScoringRule.clear();

        // build on node1
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(3, testScoringRule.calledWorkChunkList.size());
        // this is not promised, but seems always true.
        assertEquals(testingProject, testScoringRule.calledWorkChunkList.get(0).get(0));
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(30, testScoringRule.nodesScoreList.get(1).getScore(node1));
        assertEquals(15, testScoringRule.nodesScoreList.get(2).getScore(node1));
    }

    @Test
    void testBusyScore() throws Exception {
        DumbSlave node1 = createOnlineSlave("node", 3);
        DumbSlave node2 = createOnlineSlave("node", 2);

        setScoringRule(new NodeLoadScoringRule(10, 0, -2));

        FreeStyleProject testingProject = j.createFreeStyleProject();
        FreeStyleProject node1Project = j.createFreeStyleProject();
        node1Project.setAssignedNode(node1);
        node1Project.getBuildersList().add(new SleepBuilder(60 * 1000));
        node1Project.setConcurrentBuild(true);

        FreeStyleProject node2Project = j.createFreeStyleProject();
        node2Project.setAssignedNode(node2);
        node2Project.getBuildersList().add(new SleepBuilder(60 * 1000));
        node2Project.setConcurrentBuild(true);

        assertEquals(3, node1.toComputer().getExecutors().size());
        assertEquals(2, node2.toComputer().getExecutors().size());

        // node1 busy 0
        // node2 busy 0
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));

        // build on node1
        startBuild(node1Project, BUILD_TIMEOUT);

        // node1 busy 1
        // node2 busy 0
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-20, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));

        // build on node1
        startBuild(node1Project, BUILD_TIMEOUT);
        // build on node2
        startBuild(node2Project, BUILD_TIMEOUT);

        // node1 busy 2
        // node2 busy 1
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-40, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(-20, testScoringRule.nodesScoreList.get(0).getScore(node2));

        for (Executor e : node1.toComputer().getExecutors()) {
            if (e.isBusy()) {
                e.interrupt(Result.ABORTED);
            }
        }
        for (Executor e : node2.toComputer().getExecutors()) {
            if (e.isBusy()) {
                e.interrupt(Result.ABORTED);
            }
        }

        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);

        // node1 busy 0
        // node2 busy 0
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
    }

    @Test
    void testBusyScoreForTaskset() throws Exception {
        DumbSlave node1 = createOnlineSlave("node", 4);
        setScoringRule(new NodeLoadScoringRule(5, 0, -2));

        FreeStyleProject testingProject = j.createFreeStyleProject();
        testingProject.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnSameNode = new DummySubTask("Task on same node", testingProject, 1000);
        taskOnSameNode.setSameNodeConstraint(testingProject);
        DummySubTask taskOnAnotherNode1 = new DummySubTask("Task on another node 1", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnAnotherNode2 = new DummySubTask("Task on another node 2", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        testingProject.addProperty(
                new TriggerOtherProjectProperty(taskOnSameNode, taskOnAnotherNode1, taskOnAnotherNode2));

        testScoringRule.clear();

        // build on node1
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));

        assertEquals(3, testScoringRule.calledWorkChunkList.size());
        // this is not promised, but seems always true.
        assertEquals(testingProject, testScoringRule.calledWorkChunkList.get(0).get(0));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(-20, testScoringRule.nodesScoreList.get(1).getScore(node1));
        assertEquals(-30, testScoringRule.nodesScoreList.get(2).getScore(node1));
    }

    @Test
    void testDescriptor() {
        @SuppressWarnings("unused")
        NodePreferenceScoringRule.DescriptorImpl descriptor = (NodePreferenceScoringRule.DescriptorImpl)
                j.jenkins.getDescriptorOrDie(NodePreferenceScoringRule.class);
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

    private static void startBuild(AbstractProject<?, ?> p, int timeoutsecs) throws InterruptedException {
        buildStartLock.lock();
        try {
            p.scheduleBuild2(0);
            assertTrue(buildStartCondition.await(timeoutsecs, TimeUnit.SECONDS));
        } finally {
            buildStartLock.unlock();
        }
    }

    private void setScoringRule(ScoringRule scoringRule) {
        DescriptorImpl descriptor = (DescriptorImpl) j.jenkins.getDescriptorOrDie(ScoringLoadBalancer.class);
        descriptor.configure(true, true, scoringRule, testScoringRule);
    }

    @TestExtension
    @SuppressWarnings("unused")
    public static class BuildStartListener extends RunListener<Run<?, ?>> {
        @Override
        public void onStarted(hudson.model.Run<?, ?> r, TaskListener listener) {
            buildStartLock.lock();
            try {
                buildStartCondition.signalAll();
            } finally {
                buildStartLock.unlock();
            }
        }
    }
}
