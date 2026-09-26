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

import com.hartrusion.rbmksim.jev.Az5PlantState;
import java.util.Arrays;

/**
 * Rod positions for the accident-test layout in the ReactorCore comment:
 * 25 manual rods withdrawn, the other 3 manual rods in, short rods in,
 * automatic rods trimmed so absorption matches reactivity 28.
 * <p>
 * Shared by the headless transcript and the buildathon window so both animate
 * {@link NeutronFluxModel} and {@link DisplacerAccident}, not a second model.
 */
final class AccidentPlant {

    /** Standard layout: 28 manual + 5 automatic + 4 short rods of 0.6. */
    static final double MAX_ABSORPTION = 35.4;

    /** Accident-test reactivity written in the ReactorCore comment. */
    static final double REACTIVITY = 28.0;

    /** Inside the band where {@link DisplacerAccident#fluxMultiplier} is 1. */
    static final double INITIAL_FLUX_PERCENT = 4.0;

    static final double STEP_SECONDS = 0.1;
    static final double SHORT_ROD_ABSORPTION = 0.6;
    static final int SHORT_RODS = 4;

    /** Fully inserted depth used by the headless scenario, in metres. */
    static final double INSERTED_M = 7.4;

    final NeutronFluxModel flux = new NeutronFluxModel();
    private final double[] manual = new double[28];
    private final boolean[] manualInserting = new boolean[28];
    private final double[] automatic = new double[5];
    private boolean automaticInserting;

    AccidentPlant() {
        Arrays.fill(manual, 0, 25, 0.0);
        Arrays.fill(manual, 25, 28, INSERTED_M);
        double fixed = 25 * DisplacerAccident.manualRodAbsorption(0.0)
                + 3 * DisplacerAccident.manualRodAbsorption(INSERTED_M)
                + SHORT_RODS * SHORT_ROD_ABSORPTION;
        double autoEach = (REACTIVITY / 100.0 * MAX_ABSORPTION - fixed) / automatic.length;
        if (autoEach <= 0.0 || autoEach >= 1.0) {
            throw new IllegalStateException("Automatic rods cannot balance reactivity 28.");
        }
        Arrays.fill(automatic, autoEach * 7.3);
        double absorption = absorptionPercent();
        flux.setStepTime(STEP_SECONDS);
        flux.setInitialConditions(absorption, REACTIVITY, INITIAL_FLUX_PERCENT);
        flux.setInputs(absorption, REACTIVITY);
        flux.run();
    }

    int manualCount() {
        return manual.length;
    }

    double manualPosition(int index) {
        return manual[index];
    }

    void commandSimultaneous() {
        Arrays.fill(manualInserting, true);
        automaticInserting = true;
    }

    void beginStaged() {
        automaticInserting = true;
        releaseStagedSlots();
    }

    void advance(double dt) {
        double step = DisplacerAccident.AZ5_INSERTION_SPEED_M_PER_S * dt;
        for (int i = 0; i < manual.length; i++) {
            if (manualInserting[i]) {
                manual[i] = Math.min(INSERTED_M, manual[i] + step);
            }
        }
        if (automaticInserting) {
            for (int i = 0; i < automatic.length; i++) {
                automatic[i] = Math.min(INSERTED_M, automatic[i] + step);
            }
        }
        releaseStagedSlots();
        flux.setInputs(effectiveAbsorption(), REACTIVITY);
        flux.run();
    }

    /**
     * Same batch rule as {@link StagedAz5}: rods already past the window
     * do not count, and only {@code insertionSlots} more may start.
     * Simultaneous mode marks every rod inserting up front, so this adds
     * nothing.
     */
    private void releaseStagedSlots() {
        int committed = 0;
        int waiting = 0;
        for (int i = 0; i < manual.length; i++) {
            if (manual[i] > DisplacerAccident.WINDOW_HIGH_M) {
                continue;
            }
            if (manualInserting[i]) {
                committed++;
            } else {
                waiting++;
            }
        }
        int slots = DisplacerAccident.insertionSlots(committed);
        if (slots == 0 || waiting == 0) {
            return;
        }
        for (int i = 0; i < manual.length && slots > 0; i++) {
            if (!manualInserting[i] && manual[i] <= DisplacerAccident.WINDOW_HIGH_M) {
                manualInserting[i] = true;
                slots--;
            }
        }
    }

    double absorptionPercent() {
        double sum = SHORT_RODS * SHORT_ROD_ABSORPTION;
        for (double position : manual) {
            sum += DisplacerAccident.manualRodAbsorption(position);
        }
        for (double position : automatic) {
            sum += DisplacerAccident.automaticRodAbsorption(position);
        }
        return sum / MAX_ABSORPTION * 100.0;
    }

    double boost() {
        double sum = 0.0;
        for (double position : manual) {
            sum += DisplacerAccident.manualDisplacerBoost(position);
        }
        return sum;
    }

    double effectiveAbsorption() {
        return absorptionPercent() - DisplacerAccident.absorptionRemoved(
                boost(), flux.getYNeutronFlux());
    }

    int rodsInWindow() {
        int count = 0;
        for (double position : manual) {
            if (DisplacerAccident.manualDisplacerBoost(position) > 0.0) {
                count++;
            }
        }
        return count;
    }

    int manualRodsNotYetClear() {
        int count = 0;
        for (double position : manual) {
            if (position <= DisplacerAccident.WINDOW_HIGH_M) {
                count++;
            }
        }
        return count;
    }

    int withdrawnAboveWindow() {
        int count = 0;
        for (double position : manual) {
            if (position < DisplacerAccident.WINDOW_LOW_M) {
                count++;
            }
        }
        return count;
    }

    double orm() {
        double sum = SHORT_RODS * SHORT_ROD_ABSORPTION;
        for (double position : manual) {
            sum += DisplacerAccident.manualOrmAbsorption(position);
        }
        for (double position : automatic) {
            sum += DisplacerAccident.automaticRodAbsorption(position);
        }
        return sum / MAX_ABSORPTION * 37.0;
    }

    Az5PlantState snapshot() {
        int inWindow = 0;
        for (double position : manual) {
            if (position > DisplacerAccident.WINDOW_LOW_M
                    && position <= DisplacerAccident.WINDOW_HIGH_M) {
                inWindow++;
            }
        }
        return new Az5PlantState(
                flux.getYNeutronFlux(),
                withdrawnAboveWindow(),
                inWindow,
                manual.length,
                orm());
    }
}
