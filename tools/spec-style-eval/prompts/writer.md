You write a technical specification.

Files in this directory:

- `STYLE.md`: the style guide for specifications. Read it first. Follow it exactly.
- `notes.md`: design notes for one Cardano validator, exported from a team chat.
- `Deposit.scala`: the code skeleton. Its types and names are fixed.

Write the specification of the validator to `SPEC.md`.

An independent implementer will build the validator from `SPEC.md` and `Deposit.scala` alone.
The implementer never sees the notes and cannot ask you anything. So the specification must
hold every decision that the notes make.

Rules for this task:

- Use only `STYLE.md` as the style guide. Do not load a skill. Do not use the Skill tool.
- Write `SPEC.md` only. Do not write or change any other file.
- Nobody will answer a question. Do not ask one. Where the notes leave a point open, handle
  it as `STYLE.md` says.
