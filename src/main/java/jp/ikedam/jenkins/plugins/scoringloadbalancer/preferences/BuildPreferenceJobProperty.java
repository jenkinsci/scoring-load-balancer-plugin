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

import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;

/**
 * Holds configuration that which nodes are preferred by a project.
 */
public class BuildPreferenceJobProperty extends JobProperty<AbstractProject<?, ?>>
{
    /**
     * Property name used for job configuration page.
     */
    static public final String PROPERTYNAME = "build_preference_job_property";
    
    private List<BuildPreference> buildPreferenceList;
    
    /**
     * Returns the list of preferences.
     * 
     * Each preferences holds configuration that which nodes are how preferred for this project.
     * 
     * @return the list of preferences
     */
    public List<BuildPreference> getBuildPreferenceList()
    {
        return buildPreferenceList;
    }
    
    /**
     * Constructor.
     * 
     * Initialized with values a user configured.
     * 
     * @param buildPreferenceList
     */
    @DataBoundConstructor
    public BuildPreferenceJobProperty(List<BuildPreference> buildPreferenceList)
    {
        this.buildPreferenceList = buildPreferenceList;
    }
    
    /**
     * Manages views for {@link BuildPreferenceJobProperty}
     */
    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor
    {
        /**
         * Returns the property name to hold configuration of {@link BuildPreferenceJobProperty}
         * 
         * @return the property name
         */
        public String getPropertyName()
        {
            return PROPERTYNAME;
        }
        
        /**
         * Returns the name to display
         * 
         * Displayed in Project Configuration page as a property name.
         * 
         * @return the name to display
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return Messages.BuildPreferenceJobProperty_DisplayName();
        }
        
        /**
         * Create a new instance {@link BuildPreferenceJobProperty}
         * from configurations specified by a user in System Configuration page.
         * 
         * @param req
         * @param formData
         * @return
         * @throws hudson.model.Descriptor.FormException
         * @see hudson.model.JobPropertyDescriptor#newInstance(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
         */
        @Override
        public BuildPreferenceJobProperty newInstance(StaplerRequest req,
                JSONObject formData)
                throws hudson.model.Descriptor.FormException
        {
            if(formData == null || formData.isNullObject())
            {
                return null;
            }
            JSONObject form = formData.getJSONObject(getPropertyName());
            if(form == null || form.isNullObject())
            {
                return null;
            }
            return (BuildPreferenceJobProperty)super.newInstance(req, form);
        }
    }
}
