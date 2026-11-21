This design is approved. Write the design doc and the rules into the project, the way the project keeps its docs, then commit on this branch.

Design: reuse grace period for refresh tokens.
- Problem: mobile clients retry on a bad network, and today a retry within seconds revokes the whole token family.
- Decision: accept a reused refresh token for 10 seconds after its revocation. Considered instead: a per-client retry token, which is exact but needs a change in every client. Chosen because the clients ship monthly and the risk window is short.
- Rules: the auth service accepts a reuse of a revoked token within 10 seconds of its revocation, and returns the same new token for it. A reuse later than 10 seconds still revokes the family.
- Area: auth. Nothing is built yet.
