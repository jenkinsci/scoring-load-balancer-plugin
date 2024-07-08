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

import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelExpression;
import hudson.slaves.DumbSlave;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.DescriptorImpl;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreference;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreferenceJobProperty;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreferenceNodeProperty;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.ScoringLoadBalancerJenkinsRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.TestingScoringRule;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 */
public class NodePreferenceScoringRuleJenkinsTest {
    private static int BUILD_TIMEOUT = 10;

    @Rule
    public ScoringLoadBalancerJenkinsRule j = new ScoringLoadBalancerJenkinsRule();

    TestingScoringRule testScoringRule;

    @Before
    public void setUp() throws Exception {
        testScoringRule = new TestingScoringRule();
    }

    private void setScoringRule(ScoringRule scoringRule) {
        DescriptorImpl descriptor = (DescriptorImpl) j.jenkins.getDescriptorOrDie(ScoringLoadBalancer.class);
        descriptor.configure(true, true, scoringRule, testScoringRule);
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testNodePreference() throws Exception {
        setScoringRule(new NodePreferenceScoringRule(4, -1));

        j.jenkins.getNodeProperties().add(new BuildPreferenceNodeProperty(10));
        DumbSlave node1 = j.createOnlineSlave();
        node1.getNodeProperties().add(new BuildPreferenceNodeProperty(-3));
        DumbSlave node2 = j.createOnlineSlave();

        FreeStyleProject p = j.createFreeStyleProject();
        p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(40, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-12, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testProjectPreference() throws Exception {
        setScoringRule(new NodePreferenceScoringRule(4, -1));

        DumbSlave node1 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));
        DumbSlave node2 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new BuildPreferenceJobProperty(Arrays.asList(
                new BuildPreference("master", 1),
                new BuildPreference("nodelabel", 2),
                new BuildPreference(String.format("%s && nodelabel", node1.getNodeName()), 4))));

        p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(-1, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(-6, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(-2, testScoringRule.nodesScoreList.get(0).getScore(node2));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testProjectPreferenceForMatrix() throws Exception {
        setScoringRule(new NodePreferenceScoringRule(4, 2));

        DumbSlave node1 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));
        DumbSlave node2 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));

        MatrixProject p = j.createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
        p.addProperty(new BuildPreferenceJobProperty(Arrays.asList(
                new BuildPreference("master", 1),
                new BuildPreference("nodelabel", 2),
                new BuildPreference(String.format("%s && nodelabel", node1.getNodeName()), 4))));
        p.save();

        p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(1, testScoringRule.calledWorkChunkList.size());
        assertEquals(
                p.getItem(new Combination(new AxisList(new TextAxis("axis1", "")), "value1")),
                testScoringRule.calledWorkChunkList.get(0).get(0));
        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(2, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(12, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(4, testScoringRule.nodesScoreList.get(0).getScore(node2));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testProjectPreferenceNull() throws Exception {
        setScoringRule(new NodePreferenceScoringRule(1, -1));

        DumbSlave node1 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));
        DumbSlave node2 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new BuildPreferenceJobProperty(null));

        p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
    }

    @Ignore("TODO: fix me #15")
    @Test
    public void testProjectPreferenceEmpty() throws Exception {
        setScoringRule(new NodePreferenceScoringRule(1, -1));

        DumbSlave node1 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));
        DumbSlave node2 = j.createOnlineSlave(LabelExpression.parseExpression("nodelabel"));

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new BuildPreferenceJobProperty(Collections.<BuildPreference>emptyList()));

        p.scheduleBuild2(0).get(BUILD_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(1, testScoringRule.nodesScoreList.size());
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(j.jenkins));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node1));
        assertEquals(0, testScoringRule.nodesScoreList.get(0).getScore(node2));
    }
}
