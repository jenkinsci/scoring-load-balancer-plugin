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
 *
 */
public class BuildPreferenceJobProperty extends JobProperty<AbstractProject<?, ?>>
{
    /**
     * Property name used for job configuration page.
     */
    static public final String PROPERTYNAME = "build_preference_job_property";
    
    private List<BuildPreference> buildPreferenceList;
    
    /**
     * @return the buildPreferenceList
     */
    public List<BuildPreference> getBuildPreferenceList()
    {
        return buildPreferenceList;
    }
    
    @DataBoundConstructor
    public BuildPreferenceJobProperty(List<BuildPreference> buildPreferenceList)
    {
        this.buildPreferenceList = buildPreferenceList;
    }
    
    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor
    {
        public String getPropertyName()
        {
            return PROPERTYNAME;
        }
        
        @Override
        public String getDisplayName()
        {
            return Messages.BuildPreferenceJobProperty_DisplayName();
        }
        
        @Override
        public BuildPreferenceJobProperty newInstance(StaplerRequest req,
                JSONObject formData)
                throws hudson.model.Descriptor.FormException
        {
            JSONObject form = (formData != null && !formData.isNullObject())
                    ?formData.getJSONObject(getPropertyName())
                    :new JSONObject();
            return (BuildPreferenceJobProperty)super.newInstance(req, form);
        }
    }
}
