package jp.ikedam.jenkins.plugins.scoringloadbalancer;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
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
        var loadBalancerDescriptors = rule.jenkins.getExtensionList(ScoringLoadBalancer.DescriptorImpl.class);
        assertEquals(1, loadBalancerDescriptors.size());
        assertFalse(loadBalancerDescriptors.get(0).isEnabled());
        assertTrue(loadBalancerDescriptors.get(0).isReportScoresEnabled());
        assertTrue(loadBalancerDescriptors.get(0).isSimultaneousBuildsWorkaroundEnabled());
        assertEquals(2000, loadBalancerDescriptors.get(0).getSimultaneousBuildsWorkaroundThrottleTime());

        var scoringRules = loadBalancerDescriptors.get(0).getScoringRuleList();

        assertThat(scoringRules.get(0), instanceOf(NodeLoadScoringRule.class));
        var nodeLoadScoringRule = (NodeLoadScoringRule) scoringRules.get(0);
        assertEquals(20, nodeLoadScoringRule.getScale());
        assertEquals(2, nodeLoadScoringRule.getScoreForIdleExecutor());
        assertEquals(-2, nodeLoadScoringRule.getScoreForBusyExecutor());

        assertThat(scoringRules.get(1), instanceOf(NodePreferenceScoringRule.class));
        var nodePreferenceScoringRule = (NodePreferenceScoringRule) scoringRules.get(1);
        assertEquals(30, nodePreferenceScoringRule.getNodesPreferenceScale());
        assertEquals(35, nodePreferenceScoringRule.getProjectPreferenceScale());

        assertThat(scoringRules.get(2), instanceOf(BuildResultScoringRule.class));
        var buildResultScoringRule = (BuildResultScoringRule) scoringRules.get(2);
        assertEquals(15, buildResultScoringRule.getNumberOfBuilds());
        assertEquals(40, buildResultScoringRule.getScale());
        assertEquals(-3, buildResultScoringRule.getScaleAdjustForOlder());
        assertEquals(3, buildResultScoringRule.getScoreForSuccess());
        assertEquals(-4, buildResultScoringRule.getScoreForUnstable());
        assertEquals(-5, buildResultScoringRule.getScoreForFailure());
    }
}
