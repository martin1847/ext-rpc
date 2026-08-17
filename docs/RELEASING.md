# Releasing ext-rpc to Maven Central

For whoever (human or agent) cuts the next release. Read it end to end before running
anything: the second gate is **irreversible**.

Artifacts published from this repo: `tech.krpc.ext:ext-rpc` and
`tech.krpc.ext:ext-rpc-deployment`. `native-it` and `no-client-it` are verification
apps and are never published — the root `build.gradle` skips the publish wiring for
them, so they never create a `build/central-staging` directory for the bundler to pick
up.

## Preconditions

- **Branch**: ext-rpc releases from `main`. (Sibling repos differ: `krpc` and
  `ext-mybatis` release from `dev`. Do not copy this file's branch rule across repos.)
- **Version**: `gradle.properties` `version` is authoritative — bump it and land that
  commit before releasing. README install coordinates are documentation, not the source
  of truth. Breaking *packaging* changes take the minor slot, not the patch slot
  (see 1.1.0 below).
- **Credentials**: `~/.gradle/gradle.properties` holds `OSSRH_BEARER_TOKEN` plus the
  `signing.*` properties. They never enter the repo tree, logs, traces, or commits.
  `publish-central.sh` reads the token from the environment first and falls back to
  Gradle properties, so you do not need to export anything by hand.
- **Tools**: `curl`, `zip`, `unzip`, and a system `gradle` (see gotcha 1).

## The two gates

`gradle/publish-central.sh` drives the Central Portal API in `USER_MANAGED` mode, which
splits publishing into a reversible gate and an irreversible one.

```
                 gate 1 (reversible)              gate 2 (IRREVERSIBLE)
build+sign+zip ──► upload ──► VALIDATED ──► inspect ──► publish --yes ──► PUBLISHING ──► repo1
                              └─ DELETE /deployment/{id} to discard ─┘
```

### 1. Upload and wait for VALIDATED

```bash
GRADLE_CMD=/abs/path/to/gradle-central.sh gradle/publish-central.sh upload
```

`upload` runs `clean build publishAllPublicationsToCentralStagingRepository`, collects
every `*/build/central-staging` directory into one bundle zip, POSTs it, records the
deployment id in `build/central-deployment-id`, then polls status every 10s (up to 60
tries) until `VALIDATED`, `PUBLISHED`, or `FAILED`.

`GRADLE_CMD` must be a wrapper that injects the CI init script — see gotcha 2:

```bash
#!/usr/bin/env bash
exec gradle --init-script /abs/path/to/ext-rpc/.github/ci-init.gradle "$@"
```

### 2. Inspect before committing

```bash
gradle/publish-central.sh status            # defaults to the last uploaded deployment
```

Check the `purls` list — it is the exact set of coordinates you are about to make
permanent. For ext-rpc it must be exactly two entries, both at the new version, and
nothing else. If a stale module or an already-published coordinate appears, stop and
fix the bundle (gotcha 6).

**To abort at this point** — still free:

```bash
curl -X DELETE -H "Authorization: Bearer $OSSRH_BEARER_TOKEN" \
  https://central.sonatype.com/api/v1/publisher/deployment/<deployment-id>
```

### 3. Publish (irreversible)

```bash
gradle/publish-central.sh publish <deployment-id> --yes
```

`--yes` is mandatory. After this the deployment moves to `PUBLISHING` and the
coordinates can never be deleted or overwritten — a mistake can only be superseded by a
new version. Propagation to `repo1.maven.org` takes roughly **15–30 minutes**; the
Central web UI shows it sooner. Do not "fix" a release by re-uploading the same version.

## Gotchas

1. **Do not use `./gradlew`.** The wrapper is gitignored in this repo (`.gitignore`
   entries `gradlew*` and `wrapper/`), so it is not part of a fresh clone and its local
   version is whatever a developer happens to have. Use the system `gradle` — 9.6.0 at
   the time of writing, which is also what CI pins via `gradle/actions/setup-gradle`.
2. **`~/.m2/settings.xml` can silently break resolution.** A configured Aliyun (or any
   other) mirror intercepts `mavenLocal()`/Central lookups and blocks fallthrough to
   Maven Central, so the build fails to resolve `tech.krpc:rpc-*`. Always drive the
   release through `--init-script .github/ci-init.gradle`, which clears the repository
   list down to Maven Central for every project and for plugin resolution. This is the
   same script CI uses; the committed `build.gradle` keeps `mavenLocal()` first for
   local iteration and is deliberately left untouched.
3. **Credentials stay out of the tree.** `OSSRH_BEARER_TOKEN` and `signing.*` live only
   in `~/.gradle/gradle.properties` (and the external vault). Never echo the token, and
   never paste a status/upload response containing it into a log, issue, or transcript.
4. **Read build failures from the full log file, not a pipe tail.** The Gradle failure
   summary is far from the end of the output. Tee the run to a file and then:
   `grep -A8 "What went wrong" /tmp/release-build.log`. `tail | grep` routinely hides
   the actual cause.
5. **Branch discipline**: ext-rpc from `main`. `krpc` and `ext-mybatis` release from
   `dev`. Getting this wrong publishes a tree nobody reviewed.
6. **The bundler globs `*/build/central-staging`.** It picks up whatever is there, so a
   stale staging directory from an earlier version would be uploaded alongside the new
   one. `upload` runs `clean` first, which handles the normal case; if you ever build a
   bundle by hand, verify the `purls` list in step 2 rather than trusting the glob. A
   *new* version never collides with an existing coordinate — the failure mode to watch
   for is an *already-published* coordinate sneaking back into the bundle.

## Worked example — 1.1.0 (2026-08-17)

The breaking-packaging release that zeroed the `tech.krpc` dependencies out of the
published POMs.

- Version chosen: **1.1.0**, not 1.0.6. Dropping the transitive `rpc-client` is a
  breaking packaging change for consumers that relied on ext-rpc to supply it, so it
  takes the minor slot.
- Deployment id: `43ebb017-c96c-4c1e-b133-e5141c16a3f5`
- `purls` at the VALIDATED gate — exactly two, nothing else:
  - `pkg:maven/tech.krpc.ext/ext-rpc@1.1.0`
  - `pkg:maven/tech.krpc.ext/ext-rpc-deployment@1.1.0`
- Central returned a **quota warning**. It is informational today: enforcement begins
  **2026-10-01**. It does not block a publish, and it is not a reason to abort a
  release — but it is a reason not to burn versions on throwaway uploads.
- Post-publish verification: the POMs on Central must contain zero `tech.krpc`
  dependencies (only the `tech.krpc.ext` self-coordinates). That invariant is described
  in `AGENTS.md` and guarded in-build by the `no-client-it` module.

## After publishing

- Confirm the artifacts appear under `https://repo1.maven.org/maven2/tech/krpc/ext/`
  after propagation.
- Update the README changelog and install coordinates.
- Downstream bumps (for example `krpc`'s `extRpcVersion`) are a separate change in a
  separate repo — never bundled into the release commit here.
