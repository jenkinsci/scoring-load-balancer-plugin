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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.util.FormValidation;
import java.util.Arrays;
import java.util.Collections;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.preferences.BuildPreference.DescriptorImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 *
 */
@WithJenkins
class BuildPreferenceJenkinsTest {
    Item item = mock(Item.class);

    @Disabled("TODO: fix me #15")
    @Test
    void testDescriptor_doCheckLabelExpressionOk(JenkinsRule j) throws Exception {
        j.createSlave("node1", "label1", null);
        DescriptorImpl descriptor = getDescriptor(j);

        {
            FormValidation v = descriptor.doCheckLabelExpression("master", null);
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("master || node1", null);
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("!label1", null);
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("  master  ", null);
            assertEquals(FormValidation.Kind.OK, v.kind);
        }
    }

    @Test
    void testDescriptor_doCheckLabelExpressionWarning(JenkinsRule j) throws Exception {
        j.createSlave("node1", "label1", null);
        DescriptorImpl descriptor = getDescriptor(j);
        {
            FormValidation v = descriptor.doCheckLabelExpression("nolabel", null);
            assertEquals(FormValidation.Kind.WARNING, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("master && node1", null);
            assertEquals(FormValidation.Kind.WARNING, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("master && node1", item);
            assertEquals(FormValidation.Kind.WARNING, v.kind);
        }
    }

    @Test
    void testDescriptor_doCheckLabelExpressionError(JenkinsRule j) {
        DescriptorImpl descriptor = getDescriptor(j);
        {
            FormValidation v = descriptor.doCheckLabelExpression(null, null);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("", null);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("   ", null);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("a b c", null);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckLabelExpression("a b c", item);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
    }

    @Disabled("TODO: fix me #15")
    @Test
    void testDescriptor_doAutoCompleteLabelExpression(JenkinsRule j) throws Exception {
        j.createSlave("node1", "label1", null);
        j.createSlave("node2", "label1", null);

        DescriptorImpl descriptor = getDescriptor(j);
        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression(null, null);
            assertEquals(Collections.emptyList(), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("", null);
            assertEquals(Collections.emptyList(), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("  ", null);
            assertEquals(Collections.emptyList(), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("m", null);
            assertEquals(Arrays.asList("master"), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("n", null);
            assertEquals(Arrays.asList("node1", "node2"), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("l", null);
            assertEquals(Arrays.asList("label1"), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("x", null);
            assertEquals(Collections.emptyList(), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("label1 || m", null);
            assertEquals(Arrays.asList("master"), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("label1 || ", null);
            assertEquals(Collections.emptyList(), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("  m  ", null);
            assertEquals(Arrays.asList("master"), c.getValues());
        }

        {
            AutoCompletionCandidates c = descriptor.doAutoCompleteLabelExpression("!m", null);
            assertEquals(Collections.emptyList(), c.getValues());
        }
    }

    private static DescriptorImpl getDescriptor(JenkinsRule j) {
        return (DescriptorImpl) j.jenkins.getDescriptorOrDie(BuildPreference.class);
    }
}
