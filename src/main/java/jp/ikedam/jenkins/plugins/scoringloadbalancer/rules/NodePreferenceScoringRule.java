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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelExpression;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import hudson.model.queue.SubTask;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreference;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreferenceJobProperty;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreferenceNodeProperty;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.util.ValidationUtil;

/**
 * Scores nodes depending on preferences of nodes and projects.
 */
public class NodePreferenceScoringRule extends ScoringRule
{
    private static Logger LOGGER = Logger.getLogger(NodePreferenceScoringRule.class.getName());
    
    private int nodesPreferenceScale;
    private int projectPreferenceScale;
    
    /**
     * @return the nodesPreferenceScale
     */
    public int getNodesPreferenceScale()
    {
        return nodesPreferenceScale;
    }
    
    /**
     * @return the projectPreferenceScale
     */
    public int getProjectPreferenceScale()
    {
        return projectPreferenceScale;
    }
    
    /**
     * Constructor.
     * 
     * Initialized with values a user configured.
     * 
     * @param nodesPreferenceScale
     * @param projectPreferenceScale
     */
    @DataBoundConstructor
    public NodePreferenceScoringRule(int nodesPreferenceScale, int projectPreferenceScale)
    {
        this.nodesPreferenceScale = nodesPreferenceScale;
        this.projectPreferenceScale = projectPreferenceScale;
    }
    
    /**
     * Scores nodes depending on preferences of nodes and projects.
     * 
     * @param task
     * @param wc
     * @param m
     * @param nodesScore
     * @return
     * @see jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule#updateScores(hudson.model.Queue.Task, hudson.model.queue.MappingWorksheet.WorkChunk, hudson.model.queue.MappingWorksheet.Mapping, jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer.NodesScore)
     */
    @Override
    public boolean updateScores(Task task, WorkChunk wc, Mapping m,
            NodesScore nodesScore)
    {
        // scores by preference configured in nodes.
        for(Node node: nodesScore.getNodes())
        {
            List<BuildPreferenceNodeProperty> prefList = 
                    Util.filter(node.getNodeProperties(), BuildPreferenceNodeProperty.class);
            if(prefList == null || prefList.isEmpty())
            {
                continue;
            }
            BuildPreferenceNodeProperty pref = prefList.get(0);
            nodesScore.addScore(node, pref.getPreference() * getNodesPreferenceScale());
        }
        
        // scores by preference configured in projects.
        for(SubTask subtask: wc)
        {
            BuildPreferenceJobProperty prefs = getBuildPreferenceJobProperty(subtask);
            if(prefs == null || prefs.getBuildPreferenceList() == null)
            {
                continue;
            }
            
            for(BuildPreference pref: prefs.getBuildPreferenceList())
            {
                try
                {
                    Label l = LabelExpression.parseExpression(pref.getLabelExpression());
                    for(Node node: nodesScore.getNodes())
                    {
                        if(!l.contains(node))
                        {
                            continue;
                        }
                        nodesScore.addScore(node, pref.getPreference() * getProjectPreferenceScale());
                    }
                }
                catch(IllegalArgumentException e)
                {
                    LOGGER.log(Level.WARNING, String.format("Skipped an invalid label: %s (configured in %s)", pref.getLabelExpression(), subtask.toString()), e);
                }
            }
        }
        
        return true;
    }
    
    private BuildPreferenceJobProperty getBuildPreferenceJobProperty(SubTask subtask)
    {
        if(!(subtask instanceof Job))
        {
            return null;
        }
        
        if(subtask instanceof MatrixConfiguration)
        {
            MatrixConfiguration conf = (MatrixConfiguration)subtask;
            return conf.getParent().getProperty(BuildPreferenceJobProperty.class);
        }
        
        Job<?,?> job = (Job<?,?>)subtask;
        return job.getProperty(BuildPreferenceJobProperty.class);
        
    }

    /**
     * Manages views for {@link NodePreferenceScoringRule}
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ScoringRule>
    {
        /**
         * Returns the name to display.
         * 
         * Displayed in System Configuration page, as a name of a scoring rule.
         * 
         * @return the name to display
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return Messages.NodePreferenceScoringRule_DisplayName();
        }
        
        /**
         * Verify the input nodesPreferenceScale.
         * 
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckNodesPreferenceScale(@QueryParameter String value)
        {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }
        
        /**
         * Verify the input projectPreferenceScale.
         * 
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckProjectPreferenceScale(@QueryParameter String value)
        {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }
    }
}
