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

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.util.FormValidation;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.rules.BuildResultScoringRule.DescriptorImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Now needs jenkins too for FormValidation
 */
@WithJenkins
class BuildResultScoringRuleNoJenkinsTest {

    @Test
    void testBuildResultScoringRule(JenkinsRule j) {
        {
            BuildResultScoringRule target = new BuildResultScoringRule(10, 123, -3, 5, -2, -9);
            assertEquals(10, target.getNumberOfBuilds());
            assertEquals(123, target.getScale());
            assertEquals(-3, target.getScaleAdjustForOlder());
            assertEquals(5, target.getScoreForSuccess());
            assertEquals(-2, target.getScoreForUnstable());
            assertEquals(-9, target.getScoreForFailure());
        }
        {
            BuildResultScoringRule target = new BuildResultScoringRule(5, 31, -2, 1, -1, -1);
            assertEquals(5, target.getNumberOfBuilds());
            assertEquals(31, target.getScale());
            assertEquals(-2, target.getScaleAdjustForOlder());
            assertEquals(1, target.getScoreForSuccess());
            assertEquals(-1, target.getScoreForUnstable());
            assertEquals(-1, target.getScoreForFailure());
        }
    }

    @Test
    void testDescriptor_doCheckNumberOfBuilds(JenkinsRule j) {
        DescriptorImpl descriptor = new DescriptorImpl();
        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("999");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("099");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("   987654321    ");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds(null);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("   ");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("1f");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("1.2");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("0.0");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("-99");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("-099");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("0");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("-0");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("00");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = descriptor.doCheckNumberOfBuilds("-00");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            long value = Integer.MAX_VALUE;
            value += 1;
            FormValidation v = descriptor.doCheckNumberOfBuilds(String.format("%d", value));
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            long value = Integer.MIN_VALUE;
            value -= 1;
            FormValidation v = descriptor.doCheckNumberOfBuilds(String.format("%d", value));
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
    }
}
