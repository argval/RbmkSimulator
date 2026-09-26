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

import com.hartrusion.rbmksim.jev.Az5Decision;
import com.hartrusion.rbmksim.jev.Az5Guard;
import com.hartrusion.rbmksim.jev.Az5PlantState;
import com.hartrusion.rbmksim.jev.JevClient;
import com.hartrusion.rbmksim.jev.JevUnavailableException;
import java.util.Arrays;

/**
 * Headless before/after of the accident sequence described in
 * {@code ReactorCore}: reactivity held at 28 by the automatic rods, 25 of 28
 * manual rods withdrawn, neutron flux in the band where the displacer term is
 * armed, then AZ-5.
 * <p>
 * The neutron integration is {@link NeutronFluxModel}. Rod absorption and the
 * displacer subtraction are {@link DisplacerAccident}. No thermal-hydraulic
 * network is run, because that network is not what trips this excursion. The
 * comment in {@code ReactorCore.run} says the void coefficient was already
 * being handled and the core was calm until AZ-5 drove the manual rods
 * through the displacer window together.
 */
public final class ChernobylAccidentDemo {

    /** Standard layout: 28 manual + 5 automatic + 4 short rods of 0.6. */
    static final double MAX_ABSORPTION = 35.4;

    /** Accident-test reactivity written in the ReactorCore comment. */
    static final double REACTIVITY = 28.0;

    /** Inside the band where {@link DisplacerAccident#fluxMultiplier} is 1. */
    static final double INITIAL_FLUX_PERCENT = 4.0;

    static final double STEP_SECONDS = 0.1;
    static final double SHORT_ROD_ABSORPTION = 0.6;
    static final int SHORT_RODS = 4;

    private ChernobylAccidentDemo() {
    }

    public static void main(String[] args) {
        selfCheck();
        String mode = args.length == 0 ? "both" : args[0];
        int status = switch (mode) {
            case "unguarded" -> run(false).excursion ? 0 : 1;
            case "guarded" -> run(true).excursion ? 1 : 0;
            case "both" -> {
                Outcome unguarded = run(false);
                Outcome guarded = run(true);
                boolean ok = unguarded.excursion && !guarded.excursion;
                System.out.println();
                if (ok) {
                    System.out.println("BEFORE/AFTER: unguarded AZ-5 entered the prompt excursion. "
                            + "The guarded AZ-5 did not.");
                } else {
                    System.out.println("BEFORE/AFTER FAILED: unguarded excursion="
                            + unguarded.excursion + ", guarded excursion=" + guarded.excursion);
                }
                yield ok ? 0 : 1;
            }
            default -> {
                System.err.println("Usage: ChernobylAccidentDemo [unguarded|guarded|both]");
                yield 2;
            }
        };
        System.exit(status);
    }

    private static Outcome run(boolean guarded) {
        Plant plant = new Plant();
        System.out.println();
        System.out.println(guarded
                ? "=== GUARDED AZ-5 (Jev or local stand-in) ==="
                : "=== UNGUARDED AZ-5 (original simultaneous insertion) ===");
        System.out.printf("Initial flux %.2f%%, reactivity %.2f, rod absorption %.2f%%%n",
                plant.flux.getYNeutronFlux(), REACTIVITY, plant.absorptionPercent());
        System.out.printf("Manual rods withdrawn above the window: %d of %d. ORM %.2f equivalent rods (simulator formula).%n",
                plant.withdrawnAboveWindow(), plant.manual.length, plant.orm());
        System.out.println("Columns: flux is percent of nominal (floor is 0.0001%, shown as 0.000). "
                + "inWindow is manual rods inside 0.75-1.25 m. boost is their displacer sum.");

        if (guarded) {
            Az5PlantState state = plant.snapshot();
            Az5Decision decision = Az5Guard.evaluate(state);
            printDecision(decision);
            if (!decision.stageInsertion()) {
                System.out.println("Guard allowed simultaneous insertion.");
                plant.commandSimultaneous();
            } else {
                System.out.println("Guard diverted AZ-5 to staged insertion, limit "
                        + DisplacerAccident.SIMULTANEOUS_LIMIT + " manual rods not yet clear of "
                        + DisplacerAccident.WINDOW_HIGH_M + " m.");
                plant.beginStaged();
            }
        } else {
            System.out.println("No guard. Every manual rod starts in together at "
                    + DisplacerAccident.AZ5_INSERTION_SPEED_M_PER_S + " m/s.");
            plant.commandSimultaneous();
        }

        Outcome outcome = new Outcome();
        double time = 0.0;
        printRow(time, plant);
        for (int step = 0; step < 200; step++) {
            plant.advance(STEP_SECONDS);
            time += STEP_SECONDS;
            if (step % 5 == 4 || plant.flux.isPromptExcursion()) {
                printRow(time, plant);
            }
            if (plant.flux.isPromptExcursion()) {
                outcome.excursion = true;
                outcome.excursionTime = time;
                int extra = 0;
                while (extra < 40 && plant.flux.getYNeutronFlux() < 500.0) {
                    plant.advance(STEP_SECONDS);
                    time += STEP_SECONDS;
                    extra++;
                    if (extra % 5 == 0 || plant.flux.getYNeutronFlux() >= 500.0) {
                        printRow(time, plant);
                    }
                }
                System.out.printf("PROMPT EXCURSION latched at t=%.1f s. Flux kept climbing past "
                        + "the model's intact limit (500%%) and does not recover.%n",
                        outcome.excursionTime);
                return outcome;
            }
            if (plant.flux.getYNeutronFlux() <= 1.1e-4 && plant.manualRodsNotYetClear() == 0) {
                printRow(time, plant);
                outcome.finalFlux = plant.flux.getYNeutronFlux();
                System.out.printf("Scram finished at t=%.1f s. Flux is at the model floor. "
                        + "No prompt excursion.%n", time);
                return outcome;
            }
        }
        outcome.finalFlux = plant.flux.getYNeutronFlux();
        System.out.printf("No prompt excursion after %.0f s. Flux %.3f%%. Reactor model still intact.%n",
                time, outcome.finalFlux);
        return outcome;
    }

    private static void printDecision(Az5Decision decision) {
        System.out.println("Guard source: " + decision.source());
        System.out.println("Reported model: " + decision.model());
        System.out.printf("az5_would_spike.noul = %.3f (act at >= %.1f)%n",
                decision.spikeNoul(), Az5Guard.SPIKE_NOUL_THRESHOLD);
        System.out.println("scram_action.choice = " + decision.choice());
        if (Az5Decision.SOURCE_JEV.equals(decision.source())
                && Double.isFinite(decision.choiceConfidence())) {
            System.out.printf("scram_action.confidence = %.3f%n", decision.choiceConfidence());
        }
        System.out.println(decision.detail());
    }

    private static void printRow(double time, Plant plant) {
        System.out.printf("t=%5.1f s  flux=%8.3f%%  inWindow=%2d  boost=%6.2f  absorption=%6.2f%%  k=%6.3f  excursion=%s%n",
                time,
                plant.flux.getYNeutronFlux(),
                plant.rodsInWindow(),
                plant.boost(),
                plant.effectiveAbsorption(),
                plant.flux.getYK(),
                plant.flux.isPromptExcursion() ? "YES" : "no");
    }

    private static void selfCheck() {
        if (DisplacerAccident.manualDisplacerBoost(1.0) != 1.0
                || DisplacerAccident.manualDisplacerBoost(0.5) != 0.0
                || DisplacerAccident.manualDisplacerBoost(2.0) != 0.0) {
            throw new IllegalStateException("Displacer window formula drifted.");
        }
        double fatal = DisplacerAccident.absorptionRemoved(25.0, 4.0);
        double hidden = DisplacerAccident.absorptionRemoved(16.0, 4.0);
        if (Math.abs(fatal - 31.5) > 1e-9 || hidden != 0.0) {
            throw new IllegalStateException("Absorption removal formula drifted.");
        }
        if (DisplacerAccident.fluxMultiplier(4.0) != 1.0
                || DisplacerAccident.fluxMultiplier(12.0) != 0.0) {
            throw new IllegalStateException("Flux multiplier drifted.");
        }
        try {
            Az5Decision parsed = JevClient.parseDecision("""
                    {"model":"jev-1.13.0","answers":{"az5_would_spike":{"type":"noul","noul":0.92},"scram_action":{"type":"choice","choice":"stage_insertion","probabilities":{"allow_simultaneous_az5":0.08,"stage_insertion":0.92},"confidence":0.82}},"usage":{"input_tokens":10,"output_tokens":4}}
                    """);
            if (!parsed.stageInsertion() || !"jev-1.13.0".equals(parsed.model())) {
                throw new IllegalStateException("Sample Jev response was not staged.");
            }
            JevClient.checkRequestShape(new Az5PlantState(4.0, 25, 0, 28, 9.58));
        } catch (JevUnavailableException ex) {
            throw new IllegalStateException("Sample Jev response failed to parse.", ex);
        }
    }

    private static final class Outcome {
        boolean excursion;
        double excursionTime;
        double finalFlux;
    }

    /**
     * Rod positions for the accident-test layout in the ReactorCore comment:
     * 25 manual rods withdrawn, the other 3 manual rods in, short rods in,
     * automatic rods trimmed so absorption matches reactivity 28.
     */
    private static final class Plant {
        final NeutronFluxModel flux = new NeutronFluxModel();
        final double[] manual = new double[28];
        final boolean[] manualInserting = new boolean[28];
        final double[] automatic = new double[5];
        boolean automaticInserting;

        Plant() {
            Arrays.fill(manual, 0, 25, 0.0);
            Arrays.fill(manual, 25, 28, 7.4);
            double fixed = 25 * DisplacerAccident.manualRodAbsorption(0.0)
                    + 3 * DisplacerAccident.manualRodAbsorption(7.4)
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
                    manual[i] = Math.min(7.4, manual[i] + step);
                }
            }
            if (automaticInserting) {
                for (int i = 0; i < automatic.length; i++) {
                    automatic[i] = Math.min(7.4, automatic[i] + step);
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
}
