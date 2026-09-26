/*
 * Copyright (C) 2026 RBMK Simulator contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hartrusion.rbmksim;

/**
 * The simulator's own AZ-5 displacer-spike arithmetic, in one place so the
 * core and the headless Chernobyl demo cannot drift apart.
 * <p>
 * This is not a new reactor model. The formulas are the ones previously
 * inlined in {@link ControlRod} and {@link ReactorCore}: a manual rod
 * contributes a boost only while it is between 0.75 m and 1.25 m inserted,
 * and that boost removes absorption only when the sum, scaled by the
 * low-flux multiplier, exceeds 16. {@code NeutronFluxModel} then treats a
 * large enough sudden loss of absorption as a prompt excursion.
 * <p>
 * According to the note on {@link ControlRod#calculateDisplacerBoost}, the
 * window is about one metre of travel because the modeled explosion is
 * timed near three seconds after AZ-5, and a full 7.3 m insertion is about
 * 22 seconds.
 */
public final class DisplacerAccident {

    /** Last entry of the control-rod speed table. AZ-5 selects this rate. */
    public static final double AZ5_INSERTION_SPEED_M_PER_S = 0.331;

    /** Boost is zero at and below this insertion depth. */
    public static final double WINDOW_LOW_M = 0.75;

    /** Boost is zero at and above this insertion depth. */
    public static final double WINDOW_HIGH_M = 1.25;

    /**
     * Summed boost at or below this value removes no absorption. ReactorCore
     * hides the first 16 rod-equivalents so a normal scram does not spike.
     */
    public static final double HIDDEN_BOOST = 16.0;

    /** Absorption percent removed per rod-equivalent of boost above the hidden part. */
    public static final double BOOST_GAIN = 3.5;

    /**
     * Manual rods that may occupy the displacer window together before
     * {@link #absorptionRemoved} becomes positive. Equal to the hidden boost
     * because each rod contributes at most 1.0.
     */
    public static final int SIMULTANEOUS_LIMIT = 16;

    private DisplacerAccident() {
    }

    /**
     * Manual-rod absorption versus insertion depth. Identical to the manual
     * branch that used to live in {@code ControlRod.calculateAbsorption}.
     * Position 0 is fully withdrawn. Position 7.3 m is fully inserted.
     */
    /**
     * Manual-rod ORM absorption. The tip effect is omitted, matching
     * {@code ControlRod.calculateAbsorption}.
     */
    public static double manualOrmAbsorption(double positionMeters) {
        if (positionMeters <= 0.0) {
            return 0.0;
        }
        if (positionMeters >= 7.3) {
            return 1.0;
        }
        return 1.0 / 7.3 * positionMeters;
    }

    public static double manualRodAbsorption(double positionMeters) {
        if (positionMeters <= 0.4) {
            return 0.03 - positionMeters * (0.03 / 0.4);
        }
        if (positionMeters >= 7.3) {
            return 1.0;
        }
        return 1.0 / (7.3 - 0.4) * (positionMeters - 0.4);
    }

    /**
     * Automatic-rod absorption. Identical to the automatic branch in
     * {@code ControlRod.calculateAbsorption}.
     */
    public static double automaticRodAbsorption(double positionMeters) {
        if (positionMeters <= 0) {
            return 0.0;
        }
        if (positionMeters >= 7.3) {
            return 1.0;
        }
        return 1.0 / 7.3 * positionMeters;
    }

    /**
     * Displacer boost for one manual rod. Zero outside (0.75 m, 1.25 m),
     * and 1.0 at exactly 1.0 m.
     */
    public static double manualDisplacerBoost(double positionMeters) {
        if (positionMeters >= WINDOW_HIGH_M || positionMeters <= WINDOW_LOW_M) {
            return 0.0;
        }
        if (positionMeters == 1.0) {
            return 1.0;
        }
        if (positionMeters > 1.0) {
            return -4 * positionMeters + 5;
        }
        return 4 * positionMeters - 3;
    }

    /**
     * How strongly the displacer term is applied. Full strength at or below
     * 5% neutron flux, fading to zero at 10% and above. This is why the same
     * AZ-5 motion does not spike the model during a normal higher-power scram.
     */
    public static double fluxMultiplier(double neutronFluxPercent) {
        if (neutronFluxPercent <= 5.0) {
            return 1.0;
        }
        if (neutronFluxPercent <= 10.0) {
            return -0.2 * neutronFluxPercent + 2;
        }
        return 0.0;
    }

    /**
     * Absorption, in the same 0..100 percent units as {@code rodAbsorption},
     * removed by the summed manual-rod displacer boost.
     */
    public static double absorptionRemoved(double summedBoost, double neutronFluxPercent) {
        return Math.max(0.0,
                summedBoost * fluxMultiplier(neutronFluxPercent) - HIDDEN_BOOST) * BOOST_GAIN;
    }

    /**
     * How many additional manual rods may start moving without pushing the
     * number still at or above the window past {@link #SIMULTANEOUS_LIMIT}.
     */
    public static int insertionSlots(int committedNotYetClear) {
        return Math.max(0, SIMULTANEOUS_LIMIT - committedNotYetClear);
    }
}
