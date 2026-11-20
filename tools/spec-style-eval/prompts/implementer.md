Read `AGENTS.md`. Then implement the validator that `SPEC.md` specifies.

- `Deposit.scala` holds the skeleton. Write the validator there. Do not change the package,
  the object name `Deposit`, the datum `Booking`, the redeemer `Action`, or `project.scala`.
  An external test suite compiles against these exact types.
- `SPEC.md` is the only source of requirements. Where the specification and your habits
  disagree, the specification wins.
- Write your tests in `Deposit.test.scala`. Replace the scaffold test. You may delete
  `DepositIntegration.test.scala` or update it.
- Cite the specification. Put the ID of each rule next to the code that implements it, and
  next to each test that checks it, in the form `// spec [ID]`.
- `scala-cli compile .` and `scala-cli test .` must both pass when you finish.
- Nobody will answer a question. Do not ask one. If the specification does not decide a point
  that you need, choose the reading that fits the specification best, and record the point.
- When you finish, write the file `SPEC-GAPS.md`. Write one line for each point that the
  specification did not say or left unclear, in exactly this form:
  `- GAP: <the point> | CHOSE: <what you implemented>`
  If there is no such point, write the single line `NONE`.
