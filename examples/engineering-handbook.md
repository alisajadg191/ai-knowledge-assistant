# Example engineering handbook

This document describes a fictional engineering team.

## Code review

A pull request needs approval from one engineer who did not author it. Changes to authentication or access control need a second review from the security owner. All required automated checks must pass before merging.

## Deployment

Deployments are normally scheduled between 09:00 and 16:00 on working days. The change owner checks dashboards and error rates for 20 minutes after deployment. Emergency changes need the incident commander's approval.

## Documentation

Every public API should describe request fields, response fields and expected error codes. Keep setup instructions in the repository README. Mark fictional examples clearly so readers do not confuse them with production records.
