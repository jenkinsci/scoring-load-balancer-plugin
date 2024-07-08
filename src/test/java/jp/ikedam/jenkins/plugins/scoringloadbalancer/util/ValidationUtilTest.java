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

package jp.ikedam.jenkins.plugins.scoringloadbalancer.util;

import static org.junit.Assert.*;

import hudson.util.FormValidation;
import org.junit.Test;

/**
 *
 */
public class ValidationUtilTest {
    @Test
    public void testDoCheckInteger() {
        {
            FormValidation v = ValidationUtil.doCheckInteger("999");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("-99");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("0");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("-0");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("099");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("-099");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("00");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("-00");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("   987654321    ");
            assertEquals(FormValidation.Kind.OK, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger(null);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("   ");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("1f");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("1.2");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            FormValidation v = ValidationUtil.doCheckInteger("0.0");
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            long value = Integer.MAX_VALUE;
            value += 1;
            FormValidation v = ValidationUtil.doCheckInteger(String.format("%d", value));
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }

        {
            long value = Integer.MIN_VALUE;
            value -= 1;
            FormValidation v = ValidationUtil.doCheckInteger(String.format("%d", value));
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
    }
}
