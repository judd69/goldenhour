# GoldenHour

**The exact hour the light turns to gold over your city — and whether the clouds will ruin it.**

The best light of the day lasts about an hour after sunrise and an hour
before sunset, and the blue hour is the ~23 minutes of civil twilight
either side. Nobody tells you the exact minutes for *your* city today —
and a cloudy golden hour is a wasted trip. GoldenHour answers two
questions: *when is the light good today?* and *is it worth going out?*

It computes the four light windows from real sun data
([sunrise-sunset.org](https://sunrise-sunset.org), free and keyless) and
scores each one from real hourly cloud cover
([Open-Meteo](https://open-meteo.com), free and keyless). The score is an
**estimate from cloud cover only** — it says nothing about haze, dust, or
pollution, and GoldenHour says so everywhere the score appears.

- **Live dashboard:** https://judd69.github.io/goldenhour/ (calls both APIs
  directly from your browser — no account, no key, no server)
- **Java CLI + library:** zero shipped dependencies, JDK 21, JUnit 5 tests

## What it does

```
goldenhour today                  today's windows for Mumbai (the default)
goldenhour today --city Jaipur    today's windows for a preset place
goldenhour today --lat 19.07 --lon 72.87 --date 2026-11-12
goldenhour week [--city NAME]     7-day planner: best window per day
goldenhour cities                 list the 20 preset places
goldenhour --json today           machine-readable JSON
goldenhour --from-file examples today --date 2026-11-11   offline replay
goldenhour --serve [port]         serve the dashboard on localhost
```

Golden hour = sunrise to +60 min, and sunset −60 min to sunset.
Blue hour = civil twilight to sunrise, and sunset to civil twilight end.
Quality 0–100 = 100 − mean cloud cover over the window:
90+ Superb, 75+ Great, 55+ Fair, 35+ Poor, below that Skip.

## Example output

Real run on the saved snapshot (`examples/`, Mumbai, 11 Nov 2026 — a
crystal-clear day, 0% cloud at every golden-hour hour):

```
$ java -jar build/goldenhour.jar --from-file examples today --date 2026-11-11
Mumbai — Wed 11 Nov 2026 (19.0760, 72.8777)
Sunrise 06:43   Sunset 18:01   Solar noon 12:22   Day length 11h 17m
Best light: evening golden hour 17:01–18:01, quality 100/100 (Superb)

  Morning blue hour      06:20–06:43  100/100 Superb
  Morning golden hour    06:43–07:43  100/100 Superb
  Evening golden hour    17:01–18:01  100/100 Superb
  Evening blue hour      18:01–18:24  100/100 Superb

Quality is an estimate from cloud cover only — it says nothing about haze or pollution.
```

The week planner shows the contrast the score is for — 15 Nov's evening
is fully clouded, so its best light is the morning instead:

```
$ java -jar build/goldenhour.jar --from-file examples week --date 2026-11-11
Mumbai — next 7 days (19.0760, 72.8777)

  Date             Sunrise     Sunset       Best window                Quality
  Wed 11 Nov       06:43       18:01        Evening golden hour 17:01–18:01 100/100 Superb
  Thu 12 Nov       06:44       18:01        Evening golden hour 17:01–18:01 100/100 Superb
  Fri 13 Nov       06:44       18:00        Evening golden hour 17:00–18:00 100/100 Superb
  Sat 14 Nov       06:45       18:00        Evening golden hour 17:00–18:00 100/100 Superb
  Sun 15 Nov       06:45       18:00        Morning blue hour 06:22–06:45 93/100 Superb
  Mon 16 Nov       06:46       18:00        Evening golden hour 17:00–18:00 100/100 Superb
  Tue 17 Nov       06:46       17:59        Evening golden hour 16:59–17:59 100/100 Superb

Best day: Wed 11 Nov 2026 — evening golden hour 17:01–18:01, quality 100/100 (Superb).
```

JSON output (`--json today`) gives the same day as structured data —
place, sunrise/sunset/solar-noon, day length in minutes, and all four
windows with quality and verdict (see `Report.todayJson`).

## Honest labelling

- Sun times are the real sunrise-sunset.org reading for the place and date.
- Golden/blue windows are the standard 60-minute / civil-twilight
  definitions, **not** a guarantee of good light.
- The quality score comes only from Open-Meteo hourly cloud cover. A
  clear sky can still be hazy; a "Superb" day can still disappoint.
  GoldenHour never shows invented sun times or cloud readings: if an API
  is unreachable and no `--from-file` snapshot was given, the CLI exits 1
  with a clear error and the dashboard says so on the page.

## Build & test

```sh
./build.sh          # compile + run the JUnit 5 suite + package build/goldenhour.jar
java -jar build/goldenhour.jar --help
```

50 tests, all green: JSON parsing, sun/cloud snapshot parsing, window
maths and score bands against the real Mumbai snapshots, preset places,
and CLI end-to-end runs on the saved snapshots.

## Snapshots

`examples/` holds verbatim API responses fetched 11 Nov 2026 ~07:55 IST,
used by the tests and by `--from-file` offline mode:

- `sun-mumbai-2026-11-11.json` — sunrise-sunset.org, Mumbai, 11 Nov 2026
- `sun-mumbai-7d.json` — sunrise-sunset.org, Mumbai, 11–17 Nov 2026
- `cloud-mumbai-7d.json` — Open-Meteo hourly cloud cover, Mumbai,
  11–17 Nov 2026 (168 hourly points)

## License

MIT — see [LICENSE](LICENSE).
