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

import java.util.concurrent.TimeUnit;

import hudson.model.Executor;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.labels.LabelExpression;
import hudson.model.Result;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.SleepBuilder;

import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.DescriptorImpl;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.DummySubTask;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.ScoringLoadBalancerJenkinsRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TestingScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TriggerOtherProjectProperty;

/**
 *
 */
public class NodeLoadScoringRuleJenkinsTest
{
    private static int BUILD_TIMEOUT = 10;
    
    @Rule
    public ScoringLoadBalancerJenkinsRule j = new ScoringLoadBalancerJenkinsRule();
    
    TestingScoringRule testScoringRule;
    
    @Before
    public void setUp() throws Exception
    {
        testScoringRule = new TestingScoringRule();
    }
    
    private void setScoringRule(ScoringRule scoringRule)
    {
        DescriptorImpl descriptor
            = (DescriptorImpl)j.jenkins.getDescriptor(ScoringLoadBalancer.class);
        descriptor.configure(true, true, scoringRule, testScoringRule);
    }
    
    @Test
    public void testIdleScore() throws Exception
    {
        Node node1 = j.createOnlineSlave("node", 3);
        Node node2 = j.createOnlineSlave("node", 2);
        
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
        j.startBuild(node1Project, BUILD_TIMEOUT);
        
        // node1 idle 2
        // node2 idle 2
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node2));
        
        // build on node2
        j.startBuild(node2Project, BUILD_TIMEOUT);
        
        // node1 idle 2
        // node2 idle 1
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(60, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(30, testScoringRule.nodesScoreList.get(0).getScore(node2));
        
        for(Executor e: node1.toComputer().getExecutors())
        {
            if(e.isBusy())
            {
                e.interrupt(Result.ABORTED);
            }
        }
        for(Executor e: node2.toComputer().getExecutors())
        {
            if(e.isBusy())
            {
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
    public void testIdleScoreForTaskset() throws Exception
    {
        Node node1 = j.createOnlineSlave("node", 4);
        setScoringRule(new NodeLoadScoringRule(5, 3, 0));
        
        FreeStyleProject testingProject = j.createFreeStyleProject();
        testingProject.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnSameNode = new DummySubTask("Task on same node", testingProject, 1000);
        taskOnSameNode.setSameNodeConstraint(testingProject);
        DummySubTask taskOnAnotherNode1 = new DummySubTask("Task on another node 1", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnAnotherNode2 = new DummySubTask("Task on another node 2", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        testingProject.addProperty(new TriggerOtherProjectProperty(taskOnSameNode, taskOnAnotherNode1, taskOnAnotherNode2));
        
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
    public void testBusyScore() throws Exception
    {
        Node node1 = j.createOnlineSlave("node", 3);
        Node node2 = j.createOnlineSlave("node", 2);
        
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
        j.startBuild(node1Project, BUILD_TIMEOUT);
        
        // node1 busy 1
        // node2 busy 0
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-20, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
        
        // build on node1
        j.startBuild(node1Project, BUILD_TIMEOUT);
        // build on node2
        j.startBuild(node2Project, BUILD_TIMEOUT);
        
        // node1 busy 2
        // node2 busy 1
        testScoringRule.clear();
        j.assertBuildStatusSuccess(testingProject.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-40, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(-20, testScoringRule.nodesScoreList.get(0).getScore(node2));
        
        for(Executor e: node1.toComputer().getExecutors())
        {
            if(e.isBusy())
            {
                e.interrupt(Result.ABORTED);
            }
        }
        for(Executor e: node2.toComputer().getExecutors())
        {
            if(e.isBusy())
            {
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
    public void testBusyScoreForTaskset() throws Exception
    {
        Node node1 = j.createOnlineSlave("node", 4);
        setScoringRule(new NodeLoadScoringRule(5, 0, -2));
        
        FreeStyleProject testingProject = j.createFreeStyleProject();
        testingProject.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnSameNode = new DummySubTask("Task on same node", testingProject, 1000);
        taskOnSameNode.setSameNodeConstraint(testingProject);
        DummySubTask taskOnAnotherNode1 = new DummySubTask("Task on another node 1", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        DummySubTask taskOnAnotherNode2 = new DummySubTask("Task on another node 2", testingProject, 1000);
        taskOnAnotherNode1.setAssignedLabel(LabelExpression.parseExpression("node"));
        testingProject.addProperty(new TriggerOtherProjectProperty(taskOnSameNode, taskOnAnotherNode1, taskOnAnotherNode2));
        
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
}
