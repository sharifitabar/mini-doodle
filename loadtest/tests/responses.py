#!/usr/bin/env python3
"""Do local response checks with real k6 and a temporary HTTP fixture."""

import json
import os
import platform
import subprocess
import tempfile
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
IMAGE = "grafana/k6:2.3.0"
USERS = [f"10000000-0000-4000-8000-{n:012x}" for n in range(1, 7)]
# Success, create refusal, create contention, book refusal, book contention, book failure.
STATUSES = [(201, 201), (409, None), (503, None), (201, 409), (201, 503), (201, 500)]


class Fixture(BaseHTTPRequestHandler):
    requests = []

    def do_POST(self):
        user = self.headers.get("X-User-Id")
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
        self.requests.append((user, self.path))
        create = self.path == "/api/v1/slots"
        valid = user in USERS and (
            (create and body.get("durationMinutes") == 30 and "startAt" in body)
            or (
                self.path == f"/api/v1/slots/{user}/meeting"
                and body.get("title")
                and body.get("participants")
            )
        )
        status = STATUSES[USERS.index(user)][0 if create else 1] if valid else 500
        status = status or 500
        payload = json.dumps({"id": user} if status == 201 else {"status": status}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        if status == 503:
            self.send_header("Retry-After", "1")
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_args):
        pass


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def run_case(name, indexes, expected_exit, server, results):
    users = [USERS[index] for index in indexes]
    container = f"mini-doodle-response-{uuid.uuid4().hex}"
    command = ["docker", "run", "--rm", "--name", container]
    if platform.system() == "Linux":
        command += ["--add-host=host.docker.internal:host-gateway"]
    command += [
        "--user", f"{os.getuid()}:{os.getgid()}",
        "-v", f"{ROOT}:/scripts:ro",
        "-v", f"{results}:/results",
    ]
    environment = {
        "BASE_URL": f"http://host.docker.internal:{server.server_port}",
        "WINDOW_FROM": "2026-01-01T00:00:00Z",
        "WINDOW_TO": "2026-01-02T00:00:00Z",
        "SMOKE": "1",
        "TEST_ID": name,
        "RESPONSE_USERS": ",".join(users),
    }
    for key, value in environment.items():
        command += ["-e", f"{key}={value}"]
    command += [IMAGE, "run", "/scripts/tests/responses.js"]
    start = len(Fixture.requests)
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=60)
    finally:
        subprocess.run(["docker", "rm", "-f", container], capture_output=True, timeout=15)
    require(result.returncode == expected_exit, result.stdout + result.stderr)
    expected_requests = []
    for index in indexes:
        user = USERS[index]
        expected_requests.append((user, "/api/v1/slots"))
        if STATUSES[index][0] == 201:
            expected_requests.append((user, f"/api/v1/slots/{user}/meeting"))
    require(Fixture.requests[start:] == expected_requests, f"{name}: unexpected call sequence or retry")

    data = json.loads((results / f"summary-{name}.json").read_text())
    metrics = data["metrics"]
    require(metrics["iterations"]["values"]["count"] == len(users), f"{name}: iteration count")
    require(metrics["http_reqs"]["values"]["count"] == len(expected_requests), f"{name}: HTTP count")
    require((metrics["http_req_failed"]["values"]["rate"] > 0) == (expected_exit != 0),
            f"{name}: HTTP failure classification")
    for metric, status in [("business_refusals", 409), ("slot_contention", 503)]:
        counts = [sum(STATUSES[index][step] == status for index in indexes) for step in range(2)]
        total = sum(counts)
        require(metrics.get(metric, {}).get("values", {}).get("count", 0) == total,
                f"{name}: {metric} total")
        for step, count in zip(["create_slot", "book_meeting"], counts):
            key = f"{metric}{{step:{step}}}"
            require(metrics.get(key, {}).get("values", {}).get("count", 0) == count,
                    f"{name}: {key} expected {count}, metric={metrics.get(key)}")
        line = f"{metric} total: {total} (create_slot {counts[0]}, book_meeting {counts[1]})"
        require(line in result.stdout, f"{name}: summary missing {line}")
    verdict = "PASS" if expected_exit == 0 else "FAIL"
    require(f"VERDICT: {verdict}" in result.stdout, f"{name}: summary verdict")
    require((metrics["checks"]["values"]["rate"] == 1) == (expected_exit == 0),
            f"{name}: checks classification")
    print(f"PASS: {name} (k6 exit {result.returncode})")


def main():
    server = ThreadingHTTPServer(("0.0.0.0", 0), Fixture)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        with tempfile.TemporaryDirectory(prefix="mini-doodle-responses-") as directory:
            results = Path(directory)
            run_case("responses-mixed", [0, 1, 1, 2, 3, 4, 4], 0, server, results)
            run_case("responses-success", [0], 0, server, results)
            run_case("responses-unexpected", [5], 99, server, results)
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


if __name__ == "__main__":
    main()
