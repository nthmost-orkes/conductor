# Conductor Telemetry

> **Status: experimental / preview.** This is an opt-in feature under active design.
> It is **off by default** and never turns itself on.

Conductor can, **only if you turn it on**, send the maintainers a small, anonymous,
one-time-per-version summary of how it's deployed. This page is the complete,
authoritative description of that feature. Nothing is collected or sent that is
not documented here and in [`telemetry-payload.schema.json`](./telemetry-payload.schema.json).

## Why this exists

Conductor is self-hosted, so the maintainers have no way to know which parts of it
people actually run. That makes roadmap decisions guesswork. Concretely, we want to
answer questions like:

- Does anyone still run Cassandra, or can we stop investing in it?
- Which Conductor versions are still in production, so we know what's safe to EOL?
- What JVM / OS / hardware should the build target?

The data below is chosen to answer exactly those questions and nothing more.

## What it is — and isn't

**We collect:** which storage/queue/index modules are active (including both the
large-payload offload store and the newer file-storage subsystem), the Conductor
version, coarse JVM/OS/hardware facts, and **bucketed** counts of workflow/task
definitions and load (e.g. `"10-99"`, never an exact number).

**We never collect, ever:**

- Workflow names, task names, definitions, inputs, outputs, or any payload data.
- IP addresses, hostnames, secrets, connection strings, or credentials.
- Anything that identifies you — unless you *separately* choose to give an email.

All counts and rates are reported as coarse buckets specifically so that no
deployment can be fingerprinted by its exact scale.

## When it sends

**Once per Conductor version you run.** It is **not** a heartbeat and **not** a
background timer. When a server starts a version it hasn't reported before, it sends
one payload, then never sends again for that version. Upgrade to a new version → one
more send.

## The exact payload

This validates against [`telemetry-payload.schema.json`](./telemetry-payload.schema.json).

```jsonc
{
  "schema_version": 1,
  "instance_id": "b3f1c2a0-...-uuid",   // random, generated once & stored locally
  "cluster_id": null,                    // reserved for a future release
  "conductor_version": "3.21.5",
  "sent_at": "2026-08-31T00:00:00Z",
  "email": null,                         // present ONLY if you explicitly set one

  "runtime": {
    "jvm_version": "21.0.2",
    "os_name": "Linux",
    "os_arch": "aarch64",
    "cpu_count": 8,
    "max_heap_mb": 4096                   // rounded to nearest 1024 MB
  },

  "modules": {
    "db_type": "postgres",               // conductor.db.type
    "queue_type": "postgres",            // conductor.queue.type
    "indexing_type": "elasticsearch",    // conductor.indexing.type ("elasticsearch" == v7)
    "event_queue_type": "sqs",           // conductor.default-event-queue.type
    "external_payload_storage": "s3",    // large-payload offload (s3/azureblob/postgres/none)
    "file_storage": "none"               // newer file-storage subsystem; off by default
  },

  "scale": {
    "server_nodes": "1",                 // bucket (multi-node counting is a known TODO)
    "workflow_defs": "10-99",            // bucket (count only, never names)
    "task_defs": "100-999",              // bucket
    "workflows_started_bucket": "1k-9999/day",
    "avg_queue_depth_bucket": "10-99"
  }
}
```

`instance_id` is a random UUID stored in `<state-dir>/telemetry-state.json` so that
your restarts and per-version sends aren't miscounted as new installs. It is not
derived from your hostname, IP, or any hardware identifier, and is not linkable to
your identity.

## Email (a separate, optional choice)

Sharing anonymous stats and leaving an email are **two independent decisions**. The
email is present in the payload only if you explicitly set `conductor.telemetry.email`.
Enabling stats never captures an email on its own.

If — and only if — you provide one, we'll use it for security advisories for the
modules you actually run, major release announcements, and occasional research
invitations. We won't sell it, we won't spam you.

**Anonymity note:** if you leave stats-only, your payload is unlinkable to you
(random UUID, no identifying fields). If you *add* an email, you are knowingly linking
that submission to yourself — that's your choice, and this is us being upfront about it.

## How to turn it on, and see what would be sent

Telemetry is off unless you enable it:

```properties
conductor.telemetry.enabled=true
# optional: where to POST the payload. If blank, the payload is collected and logged
# at startup but never transmitted — handy for previewing.
conductor.telemetry.endpoint=
# optional, independent email consent (see above)
conductor.telemetry.email=
# where the instance id + already-sent versions are persisted
conductor.telemetry.state-dir=${user.home}/.conductor
# log the exact payload once at startup (the server-side "preview")
conductor.telemetry.log-payload-on-startup=true
```

or via environment variable:

```shell
CONDUCTOR_TELEMETRY_ENABLED=true
```

With `log-payload-on-startup=true` (the default when enabled), the server logs the
**exact** JSON it would send at startup — so you can see precisely what leaves the box
before configuring an endpoint.

> **Planned:** a first-run interactive opt-in prompt (and a `conductor telemetry
> preview` command) in the Conductor CLI, so local-dev users get an explicit,
> friendly choice. Not part of this server-side preview.

## Where the data goes

The payload schema is published in this repository and versioned via
`schema_version`, so any change is a reviewable diff — nothing is ever added silently.
The maintainers intend to **publish aggregate results back to the community**.

*(Endpoint host, retention window, and deletion-on-request policy are still being
finalized.)*
