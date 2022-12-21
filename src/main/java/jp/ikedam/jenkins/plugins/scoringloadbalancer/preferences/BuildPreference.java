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

package jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import jenkins.model.Jenkins;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.util.ValidationUtil;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelExpression;
import hudson.util.FormValidation;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import antlr.ANTLRException;

/**
 * Holds the configuration that which nodes are preferred to use.
 * 
 * Used by {@link BuildPreferenceJobProperty}.
 */
public class BuildPreference extends AbstractDescribableImpl<BuildPreference>
{
    private String labelExpression;
    
    /**
     * Returns the label expression to determine target nodes.
     * 
     * @return the label expression
     */
    public String getLabelExpression()
    {
        return labelExpression;
    }
    
    private int preference;
    
    /**
     * Returns the preference score for target nodes.
     * 
     * @return the preference score
     */
    public int getPreference()
    {
        return preference;
    }
    
    /**
     * Constructor.
     * 
     * Initialized with values a user configured.
     * 
     * @param labelExpression
     * @param preference
     */
    @DataBoundConstructor
    public BuildPreference(String labelExpression, int preference)
    {
        this.labelExpression = StringUtils.trim(labelExpression);
        this.preference = preference;
    }
    
    /**
     * Manages view for {@link BuildPreference}.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<BuildPreference>
    {
        /**
         * Returns the name to display.
         * 
         * Never used.
         * 
         * @return the name to display
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return "Preference for Nodes used in Building";
        }
        
        /**
         * Autocomplete for LabelExpression.
         * 
         * @param value
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteLabelExpression(
                @QueryParameter String value
        )
        {
            AutoCompletionCandidates c = new AutoCompletionCandidates();
            
            if(StringUtils.isEmpty(value))
            {
                return c;
            }
            
            // candidate labels
            Set<Label> labels = Jenkins.get().getLabels();
            
            // current inputting value
            StringTokenizer t = new StringTokenizer(value);
            String currentValue = null;
            while(t.hasMoreTokens())
            {
                currentValue = t.nextToken();
            }
            
            if(StringUtils.isEmpty(currentValue))
            {
                return c;
            }
            
            List<String> cands = new ArrayList<String>();
            
            for(Label l : labels)
            {
                if(l.getName().startsWith(currentValue))
                {
                    cands.add(l.getName());
                }
            }
            
            Collections.sort(cands);
            for(String s: cands)
            {
                c.add(s);
            }
            
            return c;
        }
        
        /**
         * Verify the input label expression
         * 
         * @param value
         * @return
         */
        public FormValidation doCheckLabelExpression(@QueryParameter String value)
        {
            if(StringUtils.isBlank(value))
            {
                return FormValidation.error(Messages.BuildPreference_labelExpression_requied());
            }
            
            try
            {
                Label l = LabelExpression.parseExpression(value);
                if(l.getNodes().isEmpty())
                {
                    return FormValidation.warning(Messages.BuildPreference_labelExpression_empty());
                }
            }
            catch(ANTLRException e)
            {
                return FormValidation.error(e, Messages.BuildPreference_labelExpression_invalid());
            }
            return FormValidation.ok();
        }
        
        public FormValidation doCheckPreference(@QueryParameter String value)
        {
            return ValidationUtil.doCheckInteger(value);
        }
    }
}
