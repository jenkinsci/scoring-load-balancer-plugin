package jp.ikedam.jenkins.plugins.scoringloadbalancer;

import static org.junit.Assert.*;

import hudson.ExtensionList;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.util.List;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.rules.BuildResultScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.rules.NodeLoadScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.rules.NodePreferenceScoringRule;
import org.junit.Rule;
import org.junit.Test;

public class ConfigurationAsCodeTest {

    @Rule
    public JenkinsConfiguredWithCodeRule rule = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code-test.yml")
    public void shouldSupportConfigurationAsCode() {
        ExtensionList<ScoringLoadBalancer.DescriptorImpl> loadBalancerDescriptors =
                rule.jenkins.getExtensionList(ScoringLoadBalancer.DescriptorImpl.class);
        assertEquals(1, loadBalancerDescriptors.size());
        assertFalse(loadBalancerDescriptors.get(0).isEnabled());
        assertTrue(loadBalancerDescriptors.get(0).isReportScoresEnabled());

        List<ScoringRule> scoringRules = loadBalancerDescriptors.get(0).getScoringRuleList();

        assertTrue(scoringRules.get(0) instanceof NodeLoadScoringRule);
        NodeLoadScoringRule nodeLoadScoringRule = (NodeLoadScoringRule) scoringRules.get(0);
        assertEquals(20, nodeLoadScoringRule.getScale());
        assertEquals(2, nodeLoadScoringRule.getScoreForIdleExecutor());
        assertEquals(-2, nodeLoadScoringRule.getScoreForBusyExecutor());

        assertTrue(scoringRules.get(1) instanceof NodePreferenceScoringRule);
        NodePreferenceScoringRule nodePreferenceScoringRule = (NodePreferenceScoringRule) scoringRules.get(1);
        assertEquals(30, nodePreferenceScoringRule.getNodesPreferenceScale());
        assertEquals(35, nodePreferenceScoringRule.getProjectPreferenceScale());

        assertTrue(scoringRules.get(2) instanceof BuildResultScoringRule);
        BuildResultScoringRule buildResultScoringRule = (BuildResultScoringRule) scoringRules.get(2);
        assertEquals(15, buildResultScoringRule.getNumberOfBuilds());
        assertEquals(40, buildResultScoringRule.getScale());
        assertEquals(-3, buildResultScoringRule.getScaleAdjustForOlder());
        assertEquals(3, buildResultScoringRule.getScoreForSuccess());
        assertEquals(-4, buildResultScoringRule.getScoreForUnstable());
        assertEquals(-5, buildResultScoringRule.getScoreForFailure());
    }
}
