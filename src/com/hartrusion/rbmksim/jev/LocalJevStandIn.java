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
 * Decision used when {@code POST https://api.typesafe.ai/v1/systemone} cannot
 * be called. This is not Jev. It does not sample a model and the numbers it
 * returns are not calibrated probabilities.
 * <p>
 * It answers the same two questions {@link JevQuestions} asks, using the
 * threshold already published in {@link DisplacerAccident}: stage the scram
 * when more manual rods have yet to clear the displacer window than
 * {@link DisplacerAccident#SIMULTANEOUS_LIMIT} and the flux multiplier is
 * still positive. Otherwise allow a normal simultaneous AZ-5.
 * <p>
 * A real credential replaces this class entirely. Set {@code TYPESAFE_API_KEY}
 * to a key from <a href="https://console.typesafe.ai/keys">console.typesafe.ai/keys</a>.
 * {@link JevClient} then POSTs the same state and questions. The response
 * fields {@code answers.az5_would_spike.noul} and
 * {@code answers.scram_action.choice} take the place of the values below.
 */
final class LocalJevStandIn {

    private LocalJevStandIn() {
    }

    static Az5Decision decide(Az5PlantState state, String reason) {
        boolean spike = state.manualRodsNotYetClear() > DisplacerAccident.SIMULTANEOUS_LIMIT
                && DisplacerAccident.fluxMultiplier(state.neutronFluxPercent()) > 0.0;
        String choice = spike ? Az5Decision.CHOICE_STAGE : Az5Decision.CHOICE_ALLOW;
        double noul = spike ? 1.0 : 0.0;
        String detail = reason
                + " Stand-in rule: stage when manual rods not yet clear of the window ("
                + state.manualRodsNotYetClear()
                + ") exceed "
                + DisplacerAccident.SIMULTANEOUS_LIMIT
                + " and the flux multiplier is positive. noul "
                + noul
                + " is that boolean, not a Jev probability.";
        return new Az5Decision(
                Az5Decision.SOURCE_STAND_IN,
                Az5Decision.SOURCE_STAND_IN,
                choice,
                noul,
                1.0,
                detail);
    }
}
