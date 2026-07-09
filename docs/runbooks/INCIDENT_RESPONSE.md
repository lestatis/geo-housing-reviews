# Incident Response Runbook

## Trigger examples

- unauthorized access to verification evidence;
- leaked token or admin credential;
- mass account takeover;
- public/private media exposure;
- destructive data change;
- audit-log tampering;
- active exploitation or extortion.

## Immediate actions

1. Open a private incident record and assign severity/commander.
2. Preserve evidence without copying sensitive data into chat or public issues.
3. Contain: revoke credentials, disable vulnerable path, isolate storage/account.
4. Assess affected data, users, time range and attacker capability.
5. Restore safe service or keep feature disabled.
6. Consult legal/privacy obligations and notification timelines.
7. Communicate factual updates; do not speculate.
8. After containment, eradicate root cause and monitor recurrence.

## Evidence checklist

- timestamps in UTC;
- affected service/version;
- request/audit IDs;
- access logs with sensitive fields redacted;
- credential rotation record;
- data objects affected;
- actions and decision owners.

## Recovery

- verify authorization and regression tests;
- restore from known-good backup if needed;
- confirm deletion/revocation propagated;
- re-enable gradually;
- monitor high-signal metrics.

## Post-incident

Within a reasonable period, write a blameless review: impact, timeline, root cause, detection gap, controls, owners and deadlines. Convert recurring agent mistakes into tests/hooks/rules, not only prose.
