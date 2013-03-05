/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package edu.toronto.cs.phenotips.measurements;

import static org.mockito.Mockito.when;

import javax.inject.Provider;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import edu.toronto.cs.phenotips.measurements.internal.HeightMeasurementHandler;

/**
 * Tests for the {@link MeasurementsScriptService} component.
 * 
 * @version $Id$
 * @since 1.0M1
 */
public class MeasurementsScriptServiceTest
{
    @Rule
    public final MockitoComponentMockingRule<MeasurementsScriptService> mocker =
        new MockitoComponentMockingRule<MeasurementsScriptService>(MeasurementsScriptService.class);

    @Test
    public void testGetWithNonExistentHint() throws ComponentLookupException
    {
        Provider<ComponentManager> provider =
            this.mocker.getInstance(new DefaultParameterizedType(null, Provider.class, ComponentManager.class),
                "context");

        ComponentManager cm = Mockito.mock(ComponentManager.class);
        when(provider.get()).thenReturn(cm);
        when(cm.getInstance(MeasurementHandler.class, "nothing")).thenThrow(new ComponentLookupException(""));
        Assert.assertNull(this.mocker.getComponentUnderTest().get("nothing"));
    }

    @Test
    public void testGetWithValidHint() throws ComponentLookupException
    {
        Provider<ComponentManager> provider =
            this.mocker.getInstance(new DefaultParameterizedType(null, Provider.class, ComponentManager.class),
                "context");

        ComponentManager cm = Mockito.mock(ComponentManager.class);
        when(provider.get()).thenReturn(cm);
        HeightMeasurementHandler handler = new HeightMeasurementHandler();
        when(cm.getInstance(MeasurementHandler.class, "height")).thenReturn(handler);
        Assert.assertEquals(handler, this.mocker.getComponentUnderTest().get("height"));
    }

    @Test
    public void testGetFuzzyValueP() throws ComponentLookupException
    {
        Assert.assertEquals("extreme-below-normal", this.mocker.getComponentUnderTest()
            .getFuzzyValue(Integer.MIN_VALUE));
        Assert.assertEquals("extreme-below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(-1));
        Assert.assertEquals("extreme-below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(0));
        Assert.assertEquals("extreme-below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(1));
        Assert.assertEquals("below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(2));
        Assert.assertEquals("below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(3));
        Assert.assertEquals("normal", this.mocker.getComponentUnderTest().getFuzzyValue(4));
        Assert.assertEquals("normal", this.mocker.getComponentUnderTest().getFuzzyValue(50));
        Assert.assertEquals("normal", this.mocker.getComponentUnderTest().getFuzzyValue(96));
        Assert.assertEquals("above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(97));
        Assert.assertEquals("above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(98));
        Assert.assertEquals("extreme-above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(99));
        Assert.assertEquals("extreme-above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(100));
        Assert.assertEquals("extreme-above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(101));
        Assert.assertEquals("extreme-above-normal", this.mocker.getComponentUnderTest()
            .getFuzzyValue(Integer.MAX_VALUE));
    }

    @Test
    public void testGetFuzzyValueSD() throws ComponentLookupException
    {
        Assert.assertEquals("extreme-below-normal", this.mocker.getComponentUnderTest()
            .getFuzzyValue(-Double.MAX_VALUE));
        Assert.assertEquals("extreme-below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(-3.1));
        Assert.assertEquals("extreme-below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(-3.0));
        Assert.assertEquals("below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(-2.99));
        Assert.assertEquals("below-normal", this.mocker.getComponentUnderTest().getFuzzyValue(-2.0));
        Assert.assertEquals("normal", this.mocker.getComponentUnderTest().getFuzzyValue(-1.99));
        Assert.assertEquals("normal", this.mocker.getComponentUnderTest().getFuzzyValue(0.0));
        Assert.assertEquals("normal", this.mocker.getComponentUnderTest().getFuzzyValue(1.99));
        Assert.assertEquals("above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(2.0));
        Assert.assertEquals("above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(2.99));
        Assert.assertEquals("extreme-above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(3.0));
        Assert.assertEquals("extreme-above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(3.1));
        Assert
            .assertEquals("extreme-above-normal", this.mocker.getComponentUnderTest().getFuzzyValue(Double.MAX_VALUE));
    }
}
