# Example incident runbook

This is fictional training material for Knowledge Desk.

## Reporting an incident

The first responder records the affected service, start time and user impact in an incident ticket. For a high-severity incident, notify the on-call engineer immediately and open a shared incident channel. The incident commander coordinates the response and assigns investigation tasks.

## Customer updates

For a high-severity incident, the communications lead posts an update every 30 minutes. Each update states the current impact, actions underway and time of the next update. Do not publish an unconfirmed root cause as a fact.

## Database connection pool exhaustion

Compare active connections with pool limits, inspect slow queries and check whether connections are released correctly. Review recent configuration deployments, but do not assume deployment timing proves causation. A rollback requires incident commander approval and a documented recovery plan.

## Closure

After recovery, monitor the service for 30 minutes before closing the incident. Create a follow-up review within two working days. Record the timeline, contributing factors, corrective actions and named owners. This runbook does not define staff holidays, salaries or expense limits.
