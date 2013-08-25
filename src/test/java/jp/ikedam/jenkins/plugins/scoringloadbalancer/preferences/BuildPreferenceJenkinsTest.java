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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import org.junit.Rule;
import org.junit.Test;

import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreference.DescriptorImpl;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.testutils.ScoringLoadBalancerJenkinsRule;

/**
 *
 */
public class BuildPreferenceJenkinsTest
{
    @Rule
    public ScoringLoadBalancerJenkinsRule j = new ScoringLoadBalancerJenkinsRule();
    
    private DescriptorImpl getDescriptor()
    {
        return (DescriptorImpl)j.jenkins.getDescriptorOrDie(BuildPreference.class);
    }
    
    @Test
    public void testDescriptor_doCheckLabelExpressionOk() throws Exception
    {
        j.createSlave("node1", "label1", null);
        DescriptorImpl descriptor = getDescriptor();
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("master");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("master || node1");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("!label1");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("  master  ");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }
    }
    
    @Test
    public void testDescriptor_doCheckLabelExpressionWarning() throws Exception
    {
        j.createSlave("node1", "label1", null);
        DescriptorImpl descriptor = getDescriptor();
        {
            FormValidation v = descriptor.doCheckLabelExpression("nolabel");
            assertEquals(FormValidation.Kind.WARNING, v.kind);
        }
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("master && node1");
            assertEquals(FormValidation.Kind.WARNING, v.kind);
        }
        
    }
    
    @Test
    public void testDescriptor_doCheckLabelExpressionError() throws Exception
    {
        DescriptorImpl descriptor = getDescriptor();
        {
            FormValidation v = descriptor.doCheckLabelExpression(null);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("   ");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
        
        {
            FormValidation v = descriptor.doCheckLabelExpression("a b c");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
    }
    
    @Test
    public void testDescriptor_doAutoCompleteLabelExpression() throws Exception
    {
        j.createSlave("node1", "label1", null);
        j.createSlave("node2", "label1", null);
        
        DescriptorImpl descriptor = getDescriptor();
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression(null);
            assertEquals(Collections.emptyList(), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("");
            assertEquals(Collections.emptyList(), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("  ");
            assertEquals(Collections.emptyList(), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("m");
            assertEquals(Arrays.asList("master"), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("n");
            assertEquals(Arrays.asList("node1", "node2"), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("l");
            assertEquals(Arrays.asList("label1"), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("x");
            assertEquals(Collections.emptyList(), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("label1 || m");
            assertEquals(Arrays.asList("master"), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("label1 || ");
            assertEquals(Collections.emptyList(), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("  m  ");
            assertEquals(Arrays.asList("master"), c.getValues());
        }
        
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("!m");
            assertEquals(Collections.emptyList(), c.getValues());
        }
    }
}
