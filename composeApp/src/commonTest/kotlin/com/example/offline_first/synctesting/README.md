# Sync testing

Automated tests for the offline-first sync path. Run them with:

```
./gradlew :composeApp:testDebugUnitTest
```

39 tests, all in `commonTest` and all passing on the Android/JVM target.

They are written to run on iOS too (`:composeApp:iosSimulatorArm64Test`), but that target does not
build today — and did not before this suite was added. `:composeApp:compileKotlinIosSimulatorArm64`
fails in the **main** source set with two pre-existing errors in the photo-upload path:
`StorageManager.kt:17` passes a `ByteArray` where iOS `Data` expects `NSData`, and
`FileUtils.ios.kt:6` has an unresolved `toByteArray()`. Once those are fixed the suite should run on
iOS unchanged; nothing in `synctesting/` is Android-specific.

## Layout

| Folder | Contents |
|---|---|
| `fakes/` | Stand-ins for the two I/O edges plus the connectivity source |
| `support/` | `SyncTestHarness` (wires a real `SyncManager` onto the fakes) and `TestReports` (row factory) |
| `scenarios/` | One class per network situation |
| `benchmark/` | Sync time versus queue length |

## Scenarios

| Class | Situation | Tests |
|---|---|---|
| `SyncWithInternetAvailableTest` | Baseline: connected, backend healthy | 7 |
| `SyncInAirplaneModeTest` | Radio off — nothing may be uploaded or lost | 6 |
| `SyncOnUnstableConnectivityTest` | Weak link dropping some requests | 5 |
| `DelayedSyncAfterReconnectTest` | 10 reports created offline, then reconnect | 4 |
| `SyncWhenBackendUnavailableTest` | Online, but the write is refused | 6 |
| `LargePendingQueueSyncTest` | 50 and 100 queued reports | 7 |
| `SyncDurationByReportCountBenchmark` | Sync time for N = 1, 10, 25, 50, 100 | 4 |

## PENDING versus FAILED

The two failure conditions are not interchangeable, and the scenarios assert the difference:

| Situation | Exception thrown by the store | Status the report is left in |
|---|---|---|
| Airplane mode, no signal, dropped request | `NetworkUnavailableException` | `PENDING` — "waiting for a network" |
| Write refused: security rule, quota, outage, bad data | `BackendUnavailableException` | `FAILED` — "the server said no" |

Both stay in the queue: `SyncManager.sync` reads `dao.getUnsynced()`, which is `PENDING` **and**
`FAILED`, so a rejected report is retried on every later pass and returns to `PENDING` if the device
subsequently goes offline. `FAILED` is a label for the user, never a decision to give up on a row.
The two exception types live in `commonMain` (`sync/SyncFailures.kt`) precisely because
`SyncManager` has to tell them apart; `FirestoreReportStore` maps Firestore's error codes onto them
(`UNAVAILABLE`/`DEADLINE_EXCEEDED`/`CANCELLED` are network, everything else is a rejection).

Network conditions (`fakes/NetworkCondition.kt`) are **deterministic** — `Unstable(failEveryNth = 3)`
drops attempts 3, 6, 9 … rather than rolling dice — so every count in every assertion is exact and a
run produces the same result on any machine.

## What the benchmark measures, and what it does not

`SyncDurationByReportCountBenchmark` charges each request a modelled round trip (40 ms) on
`runTest`'s **virtual clock**, which the framework advances instantly instead of really waiting. So
"modelled sync time" answers *given a round trip of R, how long does a queue of N take, and how does
that scale* — a property of `SyncManager`'s algorithm. It is **not** a measurement of Firestore and
must not be reported as one. "Local overhead" is real wall clock: the DAO writes and the loop.

Latest run:

| reports | requests | modelled sync time | per report | local overhead (real) |
|--------:|---------:|-------------------:|-----------:|----------------------:|
| 1 | 1 | 40 ms | 40.0 ms | 0.19 ms |
| 10 | 10 | 400 ms | 40.0 ms | 0.78 ms |
| 25 | 25 | 1 s | 40.0 ms | 2.11 ms |
| 50 | 50 | 2 s | 40.0 ms | 17.69 ms |
| 100 | 100 | 4 s | 40.0 ms | 12.59 ms |

The finding: **sync time is linear in queue length, and one report costs exactly one round trip.**
`uploadPending` is a sequential `for` loop with no batching and no concurrency, so a 100-report
queue costs 100 serial round trips. Local bookkeeping is negligible next to that — the overhead
column is noise-dominated (it is not monotonic), which is itself the point: the network dominates.
Batching or bounded concurrency is where sync latency would have to come down.

`aQueueThatFailsCostsTheSameNumberOfRequestsAsOneThatSucceeds` records a second finding worth
stating in the write-up: a doomed sync is not cheaper. The queue is walked in full and every report
attempted, so an offline device pays N timeouts on every pass.

## What is faked, and why

Only the two I/O edges are substituted — the production `SyncManager` runs unmodified:

- `SimulatedRemoteReportStore` replaces the `reports` Firestore collection (via `RemoteReportStore`)
- `RecordingPhotoUploader` replaces Firebase Storage (via `PhotoUploader`)
- `InMemoryReportDao` replaces Room; query semantics are reproduced faithfully (REPLACE on insert,
  `createdAt DESC`, flows that re-emit on write)
- `FakeConnectivityObserver` replaces `ConnectivityManager.NetworkCallback` / `NWPathMonitor`

`RemoteReportStore` and `PhotoUploader` were introduced for this suite. Before them `SyncManager`
built `Firebase.firestore.collection("reports")` in its own initializer and could not be constructed
at all without a live Firebase app.

## Known gaps

Be explicit about these rather than implying the suite covers them:

1. **Airplane mode is modelled, not real.** A condition that throws on every request is the app's
   own failure contract, not the device's radio. In particular the real Firestore SDK keeps its own
   offline write queue, so on a device `set()` may complete locally rather than throwing. Nothing
   here exercises that interaction.
2. **The auto-sync subscription is mirrored, not invoked.** `DelayedSyncAfterReconnectTest`
   re-creates the `collect { if (online) sync() }` shape from `MainViewModel.loadHome`; it does not
   run the ViewModel, because `AppContainer` resolves `Firebase.auth` eagerly. If that subscription
   is reshaped, this test will keep passing while the app breaks.
3. **No Room, no UI, no auth.** Nothing covers real SQLite behaviour, Compose screens, sign-in, or
   `firestore.rules` enforcement. A rules change that denies writes would surface as
   `BackendUnavailable` here — the scenario exists, but the rules themselves are untested. The
   mapping from real Firestore error codes to the two exception types is likewise unexercised: the
   simulated store throws them directly, so `FirestoreReportStore.classifying` is only verified by
   reading it.
4. **No pull sync**, because the app has none: `SyncManager` only pushes and deletes. A second
   device's reports never arrive. The tests describe the app as built, not as an ideal sync engine.
5. **Timings are modelled.** Real end-to-end figures need a device run against a live Firebase
   project, and should be reported separately from this table.
