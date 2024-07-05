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

import jp.ikedam.jenkins.plugins.scoringloadbalancer.util.ValidationUtil;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * Holds configuration that how this node is preferred.
 */
public class BuildPreferenceNodeProperty extends NodeProperty<Node>
{
    private int preference;
    
    /**
     * Returns how this node is preferred.
     * 
     * Larger value is more preferred.
     * 
     * @return the preference
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
     * @param preference
     */
    @DataBoundConstructor
    public BuildPreferenceNodeProperty(int preference)
    {
        this.preference = preference;
    }
    
    /**
     * Manage views for {@link BuildPreferenceNodeProperty}
     */
    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor
    {
        /**
         * Returns the name to display.
         * 
         * Displayed in Node Configuration page as a property name.
         * 
         * @return Returns the name to display.
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return Messages.BuildPreferenceNodeProperty_DisplayName();
        }
        
        /**
         * Verify an input preference value.
         * 
         * @param value
         * @return
         */
        @POST
        public FormValidation doCheckPreference(@QueryParameter String value)
        {
            Jenkins.get().checkPermission(Jenkins.READ);
            return ValidationUtil.doCheckInteger(value);
        }
    }
}
