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
package jp.ikedam.jenkins.plugins.scoringloadbalancer;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.model.labels.LabelExpression;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.DescriptorImpl;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.DummySubTask;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.ScoringLoadBalancerJenkinsRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TestingScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TriggerOtherProjectProperty;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test behavior of {@link ScoringLoadBalancer}
 */
public class ScoringLoadBalanderTest
{
    /**
     * How many times to build repeatedly to verify it runs on an expected node.
     */
    private static final int REPEAT_BUILD = 3;
    
    private static final int BUILD_TIMEOUT = 10;
    
    @Rule
    public ScoringLoadBalancerJenkinsRule j = new ScoringLoadBalancerJenkinsRule();
    
    TestingScoringRule scoringRule;
    DescriptorImpl descriptor;
    Slave node1;
    Slave node2;
    Slave node3;
    
    @Before
    public void setUp() throws Exception
    {
        scoringRule = new TestingScoringRule();
        descriptor = (DescriptorImpl)j.jenkins.getDescriptor(ScoringLoadBalancer.class);
        node1 = j.createOnlineSlave();
        node2 = j.createOnlineSlave();
        node3 = j.createOnlineSlave();
    }
    
    /**
     * Single task, single rule.
     * @throws Exception
     */
    @Test
    public void testSimple() throws Exception
    {
        descriptor.configure(true, true, scoringRule);
        FreeStyleProject p = j.createFreeStyleProject();
        
        // Run on master
        {
            scoringRule.scoreMap.clear();
            scoringRule.scoreMap.put(j.jenkins, 10);
            scoringRule.scoreMap.put(node1, 9);
            scoringRule.scoreMap.put(node2, 8);
            scoringRule.scoreMap.put(node3, 7);
            
            for(int i = 0; i < REPEAT_BUILD; ++i)
            {
                FreeStyleBuild b = p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
                assertNotNull(b);
                j.assertBuildStatusSuccess(b);
                
                assertEquals(j.jenkins, b.getBuiltOn());
            }
        }
        
        // Run on node2
        {
            scoringRule.scoreMap.clear();
            scoringRule.scoreMap.put(j.jenkins, -10);
            scoringRule.scoreMap.put(node1, -9);
            scoringRule.scoreMap.put(node2, 0);
            scoringRule.scoreMap.put(node3, -7);
            
            for(int i = 0; i < REPEAT_BUILD; ++i)
            {
                FreeStyleBuild b = p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
                assertNotNull(b);
                j.assertBuildStatusSuccess(b);
                
                assertEquals(node2, b.getBuiltOn());
            }
        }
    }
    
    @Test
    public void testMultipleRules() throws Exception
    {
        TestingScoringRule scoringRule2 = new TestingScoringRule();
        descriptor.configure(true, true, scoringRule, scoringRule2);
        FreeStyleProject p = j.createFreeStyleProject();
        
        // Run on node1
        {
            scoringRule.scoreMap.clear();
            scoringRule.scoreMap.put(j.jenkins, 10);
            scoringRule.scoreMap.put(node1, 9);
            scoringRule.scoreMap.put(node2, 0);
            scoringRule.scoreMap.put(node3, 0);
            
            scoringRule2.scoreMap.clear();
            scoringRule2.scoreMap.put(j.jenkins, 0);
            scoringRule2.scoreMap.put(node1, 3);
            scoringRule2.scoreMap.put(node2, 4);
            scoringRule2.scoreMap.put(node3, 5);
            
            for(int i = 0; i < REPEAT_BUILD; ++i)
            {
                FreeStyleBuild b = p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
                assertNotNull(b);
                j.assertBuildStatusSuccess(b);
                
                assertEquals(node1, b.getBuiltOn());
            }
        }
    }
    
    @Test
    public void testDisabled() throws Exception
    {
        descriptor.configure(false, true, scoringRule);
        FreeStyleProject p = j.createFreeStyleProject();
        
        FreeStyleBuild b = p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);
        
        assertEquals(0, scoringRule.calledWorkChunkList.size());
    }
    
    @Test
    public void testException() throws Exception
    {
        descriptor.configure(true, true, scoringRule);
        scoringRule.e = new Exception("Testing Exception");
        FreeStyleProject p = j.createFreeStyleProject();
        
        // Assigned by default load balancer.
        FreeStyleBuild b = p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);
        
        assertEquals(0, scoringRule.calledWorkChunkList.size());
    }
    
    @Test
    public void testStopSubsequent() throws Exception
    {
        TestingScoringRule scoringRule2 = new TestingScoringRule();
        descriptor.configure(true, true, scoringRule, scoringRule2);
        FreeStyleProject p = j.createFreeStyleProject();
        scoringRule.result = false;
        
        // Run on master (sconringRule2 is ignored)
        {
            scoringRule.scoreMap.clear();
            scoringRule.scoreMap.put(j.jenkins, 10);
            scoringRule.scoreMap.put(node1, 9);
            scoringRule.scoreMap.put(node2, 0);
            scoringRule.scoreMap.put(node3, 0);
            
            scoringRule2.scoreMap.clear();
            scoringRule2.scoreMap.put(j.jenkins, 0);
            scoringRule2.scoreMap.put(node1, 3);
            scoringRule2.scoreMap.put(node2, 4);
            scoringRule2.scoreMap.put(node3, 5);
            
            for(int i = 0; i < REPEAT_BUILD; ++i)
            {
                FreeStyleBuild b = p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
                assertNotNull(b);
                j.assertBuildStatusSuccess(b);
                
                assertEquals(j.jenkins, b.getBuiltOn());
            }
        }
    }
    
    @Test(expected=TimeoutException.class)
    public void testRejecting() throws Exception
    {
        descriptor.configure(true, true, scoringRule);
        scoringRule.reject = true;
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
    }
    
    @Test
    public void testMultipleTasks() throws Exception
    {
        descriptor.configure(true, true, scoringRule);
        
        FreeStyleProject p1 = j.createFreeStyleProject();
        FreeStyleProject p2 = j.createFreeStyleProject();
        p1.addProperty(new TriggerOtherProjectProperty(p2));
        
        FreeStyleBuild b1 = p1.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
        j.assertBuildStatusSuccess(b1);
        
        j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);
        FreeStyleBuild b2 = p2.getLastBuild();
        j.assertBuildStatusSuccess(b2);
    }
    
    @Ignore("TODO: fix me #15")
    @Test(expected=TimeoutException.class)
    public void testMultipleTasksShortage() throws Exception
    {
        descriptor.configure(true, true, scoringRule);
        
        FreeStyleProject p1 = j.createFreeStyleProject();
        p1.setAssignedLabel(LabelExpression.parseExpression("!master"));
        FreeStyleProject p2 = j.createFreeStyleProject();
        p2.setAssignedLabel(LabelExpression.parseExpression("!master"));
        p1.addProperty(new TriggerOtherProjectProperty(p2));
        
        node2.toComputer().disconnect(null).get();
        node3.toComputer().disconnect(null).get();
        
        p1.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
    }
    
    @Test
    public void testMultipleTasksWithConstraint() throws Exception
    {
        descriptor.configure(true, true, scoringRule);
        
        FreeStyleProject p1 = j.createFreeStyleProject();
        DummySubTask p2 = new DummySubTask("Bound task", p1, 5000);
        p2.setSameNodeConstraint(p1);
        p1.addProperty(new TriggerOtherProjectProperty(p2));
        
        // Run on master (only can run on master)
        {
            assertEquals(1, node1.getNumExecutors());
            assertEquals(1, node2.getNumExecutors());
            assertEquals(1, node3.getNumExecutors());
            
            scoringRule.scoreMap.clear();
            scoringRule.scoreMap.put(j.jenkins, -9999);
            scoringRule.scoreMap.put(node1, 9999);
            scoringRule.scoreMap.put(node2, 9999);
            scoringRule.scoreMap.put(node3, 9999);
            
            for(int i = 0; i < REPEAT_BUILD; ++i)
            {
                assertNull(p2.getLastBuiltOn());
                
                FreeStyleBuild b1 = p1.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
                assertNotNull(b1);
                j.assertBuildStatusSuccess(b1);
                assertEquals(j.jenkins, b1.getBuiltOn());
                
                j.waitUntilNoActivityUpTo(BUILD_TIMEOUT * 1000);
                
                assertEquals(j.jenkins, p2.getLastBuiltOn());
                p2.resetLastBuiltOn();
            }
        }
    }
    
    @Ignore("TODO: fix me #15")
    @Test(expected=TimeoutException.class)
    public void testMultipleTasksWithConstraintShortage() throws Exception
    {
        descriptor.configure(true, true, scoringRule);
        
        FreeStyleProject p1 = j.createFreeStyleProject();
        DummySubTask p2 = new DummySubTask("Bound task", p1, 5000);
        p2.setSameNodeConstraint(p1);
        p1.addProperty(new TriggerOtherProjectProperty(p2));
        p1.setAssignedLabel(LabelExpression.parseExpression("!master"));
        
        // Run on master (only can run on master)
        {
            assertEquals(1, node1.getNumExecutors());
            assertEquals(1, node2.getNumExecutors());
            assertEquals(1, node3.getNumExecutors());
            
            scoringRule.scoreMap.clear();
            scoringRule.scoreMap.put(j.jenkins, -9999);
            scoringRule.scoreMap.put(node1, 9999);
            scoringRule.scoreMap.put(node2, 9999);
            scoringRule.scoreMap.put(node3, 9999);
            
            p1.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);
        }
    }
}
