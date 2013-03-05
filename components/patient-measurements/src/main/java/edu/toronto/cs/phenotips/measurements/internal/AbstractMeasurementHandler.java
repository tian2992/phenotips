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
package edu.toronto.cs.phenotips.measurements.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.slf4j.Logger;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

import edu.toronto.cs.phenotips.measurements.MeasurementHandler;

/**
 * Base class for implementing a {@link MeasurementHandler}.
 * 
 * @version $Id$
 */
public abstract class AbstractMeasurementHandler implements MeasurementHandler, Initializable
{
    /** Tool used for computing the percentile corresponding to a given z-score. */
    private static final NormalDistribution NORMAL = new NormalDistribution();

    /**
     * Triplet storing the median (M), the generalized coefficient of variation (S), and the power in the Box-Cox
     * transformation (L) values used to compute the percentile corresponding to a given value.
     */
    protected static class LMS
    {
        /** L value, the power. */
        private double l;

        /** M value, the median. */
        private double m;

        /** S value, the generalized coefficient of variation. */
        private double s;

        /**
         * Constructor specifying all three values of the triplet.
         * 
         * @param l L value, the power
         * @param m M value, the median
         * @param s S value, the generalized coefficient of variation
         */
        public LMS(double l, double m, double s)
        {
            this.l = l;
            this.m = m;
            this.s = s;
        }

        @Override
        public String toString()
        {
            return String.format("[%.6g, %.6g, %.6g]", this.l, this.m, this.s);
        }
    }

    /** Logging helper object. */
    @Inject
    private Logger logger;

    /**
     * Table storing the LMS triplets for each month of the normal development of boys corresponding to this measurement
     * type.
     */
    private List<LMS> measurementsForAgeBoys;

    /**
     * Table storing the LMS triplets for each month of the normal development of girls corresponding to this
     * measurement type.
     */
    private List<LMS> measurementsForAgeGirls;

    /**
     * Get the name of this specific kind of measurements.
     * 
     * @return a simple name, all lowercase keyword
     */
    public abstract String getName();

    @Override
    public int valueToPercentile(boolean male, int ageInMonths, double value)
    {
        LMS lms = getLMSForAge(getLMSList(male), ageInMonths);
        return valueToPercentile(value, lms);
    }

    @Override
    public double valueToStandardDeviation(boolean male, int ageInMonths, double value)
    {
        LMS lms = getLMSForAge(getLMSList(male), ageInMonths);
        return valueToStandardDeviation(value, lms);
    }

    @Override
    public double percentileToValue(boolean male, int ageInMonths, int targetPercentile)
    {
        LMS lms = getLMSForAge(getLMSList(male), ageInMonths);
        if (lms == null) {
            return Double.NaN;
        }
        return percentileToValue(targetPercentile, lms.m, lms.l, lms.s);
    }

    @Override
    public double standardDeviationToValue(boolean male, int ageInMonths, double targetDeviation)
    {
        LMS lms = getLMSForAge(getLMSList(male), ageInMonths);
        if (lms == null) {
            return Double.NaN;
        }
        return standardDeviationToValue(targetDeviation, lms.m, lms.l, lms.s);
    }

    @Override
    public void initialize() throws InitializationException
    {
        readData();
    }

    /**
     * Read the LMS triplets for this feature from a resource file.
     */
    private void readData()
    {
        BufferedReader in = null;
        String filename = getName() + ".csv";
        this.measurementsForAgeBoys = new ArrayList<LMS>();
        this.measurementsForAgeGirls = new ArrayList<LMS>();
        try {
            in = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(filename), "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            // This should never happen, UTF-8 is always present
            in =
                new BufferedReader(
                    new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(filename)));
        }
        String line;
        try {
            while ((line = in.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length < 5) {
                    continue;
                }
                int month = Integer.parseInt(tokens[1], 10);
                double l = Double.parseDouble(tokens[2]);
                double m = Double.parseDouble(tokens[3]);
                double s = Double.parseDouble(tokens[4]);
                LMS lms = new LMS(l, m, s);
                if ("1".equals(tokens[0])) {
                    while (month >= this.measurementsForAgeBoys.size()) {
                        this.measurementsForAgeBoys.add(null);
                    }
                    this.measurementsForAgeBoys.set(month, lms);
                } else {
                    while (month >= this.measurementsForAgeGirls.size()) {
                        this.measurementsForAgeGirls.add(null);
                    }
                    this.measurementsForAgeGirls.set(month, lms);
                }
            }
        } catch (IOException ex) {
            // This shouldn't happen
            this.logger.error("Failed to read data table [{}]: {}", new Object[] {filename, ex.getMessage(), ex});
        }
    }

    /**
     * Extract the LMS triplet corresponding to a given month from the given list. If the requested month is before the
     * first element of the list, {@code null} is returned. If a valid entry corresponding to the requested month is
     * found in the list, then return that entry. If there's no entry for the requested month, but there are valid
     * entries in previous and later months, a linear interpolation of the nearest surrounding entries is computed and
     * returned. Otherwise, if the requested month is beyond the last valid entry, return the last valid entry.
     * 
     * @param list the standard list of measurements where to look in
     * @param ageInMonths the target age (in months) for which to compute the LMS triplet
     * @return a LMS triplet computed according to the rules above, possibly {@code null}
     */
    protected LMS getLMSForAge(List<LMS> list, int ageInMonths)
    {
        if (ageInMonths < 0) {
            return null;
        } else if (ageInMonths >= list.size()) {
            return list.get(list.size() - 1);
        }
        LMS result;
        result = list.get(ageInMonths);
        if (result == null) {
            int lowerAge = ageInMonths - 1;
            while (lowerAge >= 0 && list.get(lowerAge) == null) {
                --lowerAge;
            }
            if (lowerAge < 0) {
                return null;
            }
            int upperAge = ageInMonths + 1;
            while (upperAge < list.size() && list.get(upperAge) == null) {
                ++upperAge;
            }
            LMS lowerLMS = list.get(lowerAge);
            LMS upperLMS = list.get(upperAge);
            double delta = ((double) ageInMonths - lowerAge) / (upperAge - lowerAge);
            result =
                new LMS(lowerLMS.l + (upperLMS.l - lowerLMS.l) * delta, lowerLMS.m + (upperLMS.m - lowerLMS.m) * delta,
                    lowerLMS.s + (upperLMS.s - lowerLMS.s) * delta);
        }
        return result;
    }

    /**
     * Compute the percentile corresponding to a given absolute value, according to a normal distribution specified by
     * the given Box-Cox triplet.
     * 
     * @param x the absolute value to fit into the normal distribution
     * @param lms the parameters defining the normal distribution
     * @return a number between 0 and 100 (inclusive) specifying the percentile of this measurement
     */
    protected int valueToPercentile(double x, LMS lms)
    {
        if (lms == null) {
            return -1;
        }
        return valueToPercentile(x, lms.m, lms.l, lms.s);
    }

    /**
     * Compute the percentile corresponding to a given absolute value, according to a normal distribution specified by
     * the given Box-Cox triplet.
     * 
     * @param x the absolute value to fit into the normal distribution
     * @param m the M value, the median
     * @param l the L value, the power
     * @param s the S value, the generalized coefficient of variation
     * @return a number between 0 and 100 (inclusive) specifying the percentile of this measurement
     */
    protected int valueToPercentile(double x, double m, double l, double s)
    {
        double z = (l != 0) ? ((Math.pow(x / m, l) - 1) / (l * s)) : (Math.log(x / m) / s);
        double p = NORMAL.cumulativeProbability(z) * 100;
        return (int) Math.round(p);
    }

    /**
     * Compute the standard deviation corresponding to a given absolute value, according to a normal distribution
     * specified by the given Box-Cox triplet.
     * 
     * @param x the absolute value to fit into the normal distribution
     * @param lms the parameters defining the normal distribution
     * @return a number specifying how many standard deviations does this measurement deviate from the mean
     */
    protected double valueToStandardDeviation(double x, LMS lms)
    {
        if (lms == null) {
            return Double.NaN;
        }
        return valueToStandardDeviation(x, lms.m, lms.l, lms.s);
    }

    /**
     * Compute the standard deviation corresponding to a given absolute value, according to a normal distribution
     * specified by the given Box-Cox triplet.
     * 
     * @param x the absolute value to fit into the normal distribution
     * @param m the M value, the median
     * @param l the L value, the power
     * @param s the S value, the generalized coefficient of variation
     * @return a number specifying how many standard deviations does this measurement deviate from the mean
     */
    protected double valueToStandardDeviation(double x, double m, double l, double s)
    {
        return (l != 0) ? ((Math.pow(x / m, l) - 1) / (l * s)) : (Math.log(x / m) / s);
    }

    /**
     * Compute the value that would correspond to a target percentile, according to a normal distribution specified by
     * the given Box-Cox triplet.
     * 
     * @param percentile the target percentile to extract from the normal distribution, a number between 0 and 100
     *            (inclusive)
     * @param m the M value, the median
     * @param l the L value, the power
     * @param s the S value, the generalized coefficient of variation
     * @return a positive number specifying the expected measurement for the target percentile
     */
    protected double percentileToValue(int percentile, double m, double l, double s)
    {
        double correctedPercentile = percentile;
        if (percentile <= 0) {
            correctedPercentile = 0.25;
        } else if (percentile >= 100) {
            correctedPercentile = 99.75;
        }
        double z = NORMAL.inverseCumulativeProbability(correctedPercentile / 100.0);
        double x = (l != 0) ? Math.pow(z * l * s + 1, 1 / l) * m : Math.exp(z * s) * m;
        return x;
    }

    /**
     * Compute the value that would correspond to a target standard deviation, according to a normal distribution
     * specified by the given Box-Cox triplet.
     * 
     * @param deviation the target standard deviation to extract from the normal distribution
     * @param m the M value, the median
     * @param l the L value, the power
     * @param s the S value, the generalized coefficient of variation
     * @return a positive number specifying the expected measurement for the target standard deviation
     */
    protected double standardDeviationToValue(double deviation, double m, double l, double s)
    {
        return (l != 0) ? Math.pow(deviation * l * s + 1, 1 / l) * m : Math.exp(deviation * s) * m;
    }

    /**
     * Choose between the girls and boys measurements list, depending on the requested sex and on the availability of
     * distinct measurements for girls.
     * 
     * @param male {@code true} for boys, {@code false} for girls
     * @return a list of {@link LMS} triplets
     */
    protected List<LMS> getLMSList(boolean male)
    {
        if (!male && !this.measurementsForAgeGirls.isEmpty()) {
            return this.measurementsForAgeGirls;
        }
        return this.measurementsForAgeBoys;
    }
}
