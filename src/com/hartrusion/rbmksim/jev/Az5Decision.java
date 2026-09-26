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

/**
 * What the guard decided to do with an AZ-5 request.
 *
 * @param source {@code jev} when the HTTP API answered, {@code local-stand-in} otherwise
 * @param model version id returned by the API, or {@code local-stand-in}
 * @param choice {@code stage_insertion} or {@code allow_simultaneous_az5}
 * @param spikeNoul probability from the noul question, 0 to 1
 * @param choiceConfidence confidence on the choice answer, when the API sent one
 * @param detail human-readable note, including why a stand-in was used
 */
public record Az5Decision(
        String source,
        String model,
        String choice,
        double spikeNoul,
        double choiceConfidence,
        String detail) {

    public static final String CHOICE_STAGE = "stage_insertion";
    public static final String CHOICE_ALLOW = "allow_simultaneous_az5";
    public static final String SOURCE_JEV = "jev";
    public static final String SOURCE_STAND_IN = "local-stand-in";

    /**
     * Stage only when both typed answers agree and the noul is at least
     * {@link Az5Guard#SPIKE_NOUL_THRESHOLD}. A normal scram is the other branch.
     */
    public boolean stageInsertion() {
        return CHOICE_STAGE.equals(choice) && spikeNoul >= Az5Guard.SPIKE_NOUL_THRESHOLD;
    }
}
