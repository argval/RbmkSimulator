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
package com.hartrusion.rbmksim.jev;

import com.hartrusion.rbmksim.DisplacerAccident;

/**
 * The two questions sent on every AZ-5 evaluation. Kept in one file so the
 * wording can be reviewed without hunting through the client.
 * <p>
 * Question names are labels only. TypeSafe's API reference says the key is
 * not sent to the model and is not used in inference. The real question is
 * the {@code instructions} text. Both questions are asked in one
 * {@code POST /v1/systemone} call, which is the documented way to evaluate
 * several independent judgments against the same state.
 */
final class JevQuestions {

    static final String SPIKE = "az5_would_spike";
    static final String ACTION = "scram_action";

    /**
     * Pinned because the guard thresholds a noul. {@code jev-latest} is an
     * alias that currently points at this id. Override with
     * {@code TYPESAFE_MODEL} if you intentionally move versions.
     */
    static final String DEFAULT_MODEL = "jev-1.13.0";

    private JevQuestions() {
    }

    static String requestBody(Az5PlantState state, String model) {
        return """
                {
                  "model": %s,
                  "state": %s,
                  "questions": {
                    "%s": {
                      "type": "noul",
                      "instructions": "Using `manual_rods_not_yet_clear_of_window`, `model_hidden_boost_threshold`, and `flux_multiplier` from the state: would simultaneous AZ-5 put more manual rods through the displacer window at once than the hidden threshold, while the flux multiplier is still above zero, so this simulator subtracts absorption?",
                      "criteria": {
                        "true": "Yes. The not-yet-clear manual rod count is above the hidden threshold and the flux multiplier is above zero, so the modeled absorption subtraction can start.",
                        "false": "No. The not-yet-clear count is within the hidden threshold, or the flux multiplier is zero and this model applies no displacer subtraction."
                      }
                    },
                    "%s": {
                      "type": "choice",
                      "instructions": "Which AZ-5 action should code take so this simulator does not subtract absorption for the displacer spike? Judge only the fields in the state. Do not invent a different reactor model.",
                      "criteria": {
                        "allow_simultaneous_az5": "Drive every rod in together. The modeled displacer subtraction cannot start from this state.",
                        "stage_insertion": "Drive automatic rods in now, but move manual rods in successive groups that stay at or below `model_hidden_boost_threshold` until each group has passed the window. This uses the simulator's existing rod drives. It does not change the neutron equations."
                      }
                    }
                  }
                }
                """.formatted(
                JevJson.quote(model),
                stateJson(state),
                SPIKE,
                ACTION);
    }

    private static String stateJson(Az5PlantState state) {
        double fluxMultiplier = DisplacerAccident.fluxMultiplier(state.neutronFluxPercent());
        return """
                {
                  "plant": "RBMK-1000 as implemented by this simulator, low-power AZ-5 case",
                  "requested_action": "AZ-5 simultaneous insertion of every control rod at maximum speed",
                  "neutron_flux_percent": %s,
                  "flux_multiplier": %s,
                  "manual_rods_withdrawn_above_window": %d,
                  "manual_rods_in_window": %d,
                  "manual_rods_not_yet_clear_of_window": %d,
                  "manual_rods_total": %d,
                  "orm_equivalent_rods": %s,
                  "displacer_window_m": [0.75, 1.25],
                  "model_hidden_boost_threshold": 16,
                  "model_rule": "Each manual rod adds up to 1.0 of displacer boost while its insertion depth is strictly between 0.75 m and 1.25 m. Absorption percent is reduced by max(0, summed boost times flux_multiplier minus 16) times 3.5. The neutron model can enter an irreversible prompt excursion if that reduction is large. Flux multiplier is 1 at or below 5 percent flux, falls to 0 at 10 percent, and is 0 above that. Before AZ-5 the automatic regulators are holding the model steady."
                }
                """.formatted(
                number(state.neutronFluxPercent()),
                number(fluxMultiplier),
                state.manualRodsWithdrawn(),
                state.manualRodsInWindow(),
                state.manualRodsNotYetClear(),
                state.manualRodsTotal(),
                number(state.ormEquivalentRods()));
    }

    private static String number(double value) {
        if (Double.isFinite(value)) {
            return Double.toString(value);
        }
        return "null";
    }
}
