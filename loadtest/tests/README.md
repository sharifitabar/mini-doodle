# Response checks

Use Python 3 and Docker for these local checks.
Start Docker first.
From the repository root, run:

```bash
python3 loadtest/tests/responses.py
```

The command starts a temporary HTTP fixture and uses the `grafana/k6:2.3.0` image.
Docker can download the image if it is not present.
The fixture returns specified responses to the actual manipulation function in `booking.js`.
The command uses temporary result files and removes its containers after each run.
It does not use the application database or the metrics stack.

The checks include these cases:

| Case | Required result |
|---|---|
| Successful create and booking | Two HTTP calls, no refusal or contention, exit 0. |
| Create returns `409` | One business refusal for `create_slot`, no booking call, no retry. |
| Create returns `503` | One contention for `create_slot`, no booking call, no retry. |
| Booking returns `409` | One business refusal for `book_meeting`, no retry. |
| Booking returns `503` | One contention for `book_meeting`, no retry. |
| Booking returns `500` | HTTP failure and failed check, summary `FAIL`, k6 exit 99. |

The mixed run includes one success and the four expected error responses.
It repeats the create refusal and the booking contention.
It must have eleven HTTP calls, three business refusals, and three contention responses.

The refusal counts must be 2 for `create_slot` and 1 for `book_meeting`.
The contention counts must be 1 for `create_slot` and 2 for `book_meeting`.
These unequal counts also detect swapped step labels.

The summary must show the same totals and per-step counts as the JSON artifact.
The success-only run also checks the zero-counter summary.

The checks use the production response classification and tagged-metric configuration.
They remove latency and request-mix thresholds because fixture timings are not performance measurements.
The runner returns a nonzero exit code if an expected result is incorrect.
An expected k6 exit 99 in the `500` case is a successful check of failure handling.

These checks prove response handling and summary output with real k6.
They do not produce or measure a PostgreSQL deadlock.
Run them only on demand. Keep them outside Gradle, CI, and Git hooks.
