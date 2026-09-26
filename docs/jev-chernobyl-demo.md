# Jev and the Chernobyl AZ-5 demo

A live before/after for the buildathon. The unguarded run is this simulator's Chernobyl sequence. The guarded run asks Jev (TypeSafe's System One model) whether AZ-5 is safe, and diverts the scram when the answer says the modeled displacer spike would start.

## The historical hook, kept honest

On 26 April 1986 the operators of Chernobyl unit 4 pressed AZ-5, the scram, during a low-power test. The control rods then in service carried graphite displacers. As a rod drove in, the displacer entered the lower core ahead of the boron absorber. With the reactor's positive void coefficient and a very small reactivity margin, power rose sharply.

This program does not calculate that neutron physics. The project README says there is no accurate neutron simulation. `docs/Reactivity.md` says the same thing, and it says the explosion in this model is not the small rod-tip effect. It is a separate term called the displacer boost: many manual rods passing 0.75–1.25 m inserted at the same time, while neutron flux is low. The comment in `ReactorCore` says the positive void coefficient was already being handled by the automatic regulators, and the core was calm until AZ-5.

So the claim on stage is narrow:

**Jev stops the sequence this simulator actually implements.** It does not prove that a 1986 RBMK would have been saved, and it does not add reactor physics the author did not write.

## What the audience is watching

Both runs start from the accident test written in the `ReactorCore` comment:

- reactivity held at 28 by the automatic rods, so the model is steady (k = 1)
- 25 of 28 manual rods fully withdrawn
- neutron flux at 4% of nominal, which is inside the band where this model arms the displacer term (full strength at or below 5%, gone at 10%)
- simulator ORM about 9.6 equivalent rods

That 4% and that ORM are this model's setup, not a claim about the historical megawatt reading or the historical rod count.

### Unguarded

Every manual rod drives in together at the AZ-5 speed in the code, 0.331 m/s.

What you should see:

- flux stays near 4% for the first two seconds
- at about 2.5 s all 25 withdrawn rods are in the displacer window together
- at 2.9 s the line flips to `excursion=YES` and k goes to 1.007
- flux keeps climbing past 500% (the point where `NeutronFluxModel` marks the reactor no longer intact)
- by 3.9 s the rods have already left the window (`inWindow=0`) and the flux is still running away

That last point is the model, not a new invention. Once reactivity exceeds beta, the neutron model latches. Later absorption cannot unlatch it. Say that out loud. It is why "the rods were already moving in" does not save this run.

### Guarded

Same initial state. Before any rod moves, the guard asks two questions (see below). With no API key, the local stand-in answers them. You should hear that it is a stand-in, not Jev.

What you should see:

- `Guard source: local-stand-in` unless `TYPESAFE_API_KEY` is set
- `scram_action.choice = stage_insertion`
- at most 16 manual rods in the window at once (`inWindow=16`), so the summed boost stays under the model's hidden threshold of 16 and no absorption is subtracted for the spike
- flux falls instead of running away
- a second batch of 9 rods passes the window later, while flux is already at the model floor
- `Scram finished` around 7.6 s, and `excursion` never leaves `no`

The closing line of a paired run is:

`BEFORE/AFTER: unguarded AZ-5 entered the prompt excursion. The guarded AZ-5 did not.`

The process exits 0 only when that pair holds.

## How to run it

From the repo root, Java 17 or newer:

```bash
demo/run-jev-chernobyl.sh unguarded
demo/run-jev-chernobyl.sh guarded
demo/run-jev-chernobyl.sh both
```

`both` is the default. The script compiles only the neutron model, the displacer arithmetic, the Jev client, and the scenario. It does not build the Swing panels or the thermal network.

Suggested order on stage: unguarded first, then guarded. Leave the unguarded transcript on screen long enough for the room to see `excursion=YES` after the rods have left the window.

## What was integrated

The intervention point is AZ-5, `ReactorCore.shutdown()`. That is also the path the reactor protection system uses. The guard runs before the rods are told to move.

1. Plant state already in the core is packed as JSON: flux, flux multiplier, how many manual rods have not yet cleared 1.25 m, the window, and the model's own rule (subtract absorption only when summed boost times the flux multiplier exceeds 16).
2. One HTTP call, the only System One endpoint in the current TypeSafe docs:

   `POST https://api.typesafe.ai/v1/systemone`

   `Authorization: Bearer $TYPESAFE_API_KEY`

   Model pinned to `jev-1.13.0` because the guard thresholds a probability. The docs say `jev-latest` is an alias that currently points at `jev-1.13.0`. The response `model` field is the version that actually answered. Override the pin with `TYPESAFE_MODEL` if you mean to.

3. Two questions in that one call, which is how the API evaluates several judgments against the same state:

   - `az5_would_spike`, type `noul`. A yes/no probability that simultaneous AZ-5 would start this model's absorption subtraction.
   - `scram_action`, type `choice`, with exactly two options: `allow_simultaneous_az5` and `stage_insertion`.

4. Code stays in charge. It stages the insertion only when the choice is `stage_insertion` and the noul is at least 0.7 (the threshold in TypeSafe's own branching example). Otherwise every rod drives in together, which is the original AZ-5.

5. Staging uses the existing rod drives. Automatic rods, which do not contribute displacer boost, still go in immediately. Manual rods go in groups of at most 16 until each group has passed 1.25 m. The neutron equations are untouched.

On the full Swing simulator the same guard is on by default. `JEV_GUARD=off` restores simultaneous AZ-5. Building that UI still needs the sibling projects named in `CONTRIBUTING.md` (PhxNetMod, utils, jmplot, AbsoluteLayout). This demo does not.

## The stand-in, and what a real key replaces

This environment has no `TYPESAFE_API_KEY`. The endpoint is up: a call with no key returns HTTP 403, "Must supply an API key!", and a call with a bad key returns HTTP 401, "Cannot authenticate with the server. Please check your API key and try again."

Until a key is present, `LocalJevStandIn` answers the same two questions with the simulator's own threshold:

- stage when more than 16 manual rods have yet to clear the window and the flux multiplier is still positive
- otherwise allow a normal AZ-5

The stand-in noul is 1 or 0. That is the boolean above. It is not a Jev probability, and the transcript says so.

A real credential replaces that class and nothing else. Create a key at [console.typesafe.ai/keys](https://console.typesafe.ai/keys), then:

```bash
export TYPESAFE_API_KEY=your-key
demo/run-jev-chernobyl.sh guarded
```

`JevClient` POSTs the same state and questions. The transcript source becomes `jev`, and `Reported model` becomes the version id in the response (expected `jev-1.13.0` if you do not override it). The noul and the choice in that response are what the guard acts on.

`JEV_MODE=stand-in` forces the stand-in even if a key is set, so you can show the fallback on purpose. `JEV_MODE=auto` (the default) calls Jev when the key is set and falls back to the stand-in if the call fails, with the reason printed.

There is no official Java SDK. The documented clients are Python `typesafe-sdk` and JavaScript `@typesafe-ai/sdk`. Both speak this HTTP API. The Java side is a thin `HttpClient` call plus a small JSON reader, because this repo does not already depend on a JSON library.
