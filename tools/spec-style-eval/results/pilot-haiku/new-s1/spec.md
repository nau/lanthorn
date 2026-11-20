# Specification: no-show deposit validator (v1)

Capitals are normative, per BCP 14. The types `Booking` and `Action` in `Deposit.scala` are frozen. The implementer MUST NOT change them.

## Overview

*Informative.* A guest books a stay and locks a deposit in one UTxO at the validator. The datum is a `Booking`. The deposit is the lovelace in that UTxO, so the datum holds no amount. The guest shows up, cancels, or does not show up. If the guest shows up, the host releases the deposit back. The guest can cancel free until `cancelBy`. After that the guest can cancel until one hour before `checkIn`, and then the host receives an exact fee. If the guest does not show up, the host claims the deposit once a grace period after `checkIn` has passed. The validator never sees the current time. It sees only the validity range of the transaction, so it accepts a time-based action only when the range proves the condition. Each transaction spends exactly one deposit, so one payment cannot settle two deposits.

## Goals and non-goals

*Informative.* Goal: hold a deposit so that the guest recovers it by cancelling in time, and the host can keep it after a no-show. The guest must never lose more than the deposit, and the host must never receive more than the fee on a late cancel.

Non-goals. The validator does not do any of these in v1:

- It does not check the lock transaction, because the validator does not run at lock time. The app is trusted to lock correctly.
- It does not support partial release of a deposit.
- It does not support batching of several deposits in one transaction.
- It has no admin key and no pause switch. Legal has not yet decided on this, so v1 has none.
- It does not treat `host == guest` as a special case.
- It does not check native tokens.

## Assumptions

- The app locks each deposit with a datum where `cancelBy` is less than `checkIn` minus the notice period, `feeBps` is at most 5000, and the deposit is at least 10 ADA. The validator does not enforce this. The app builds the lock transaction. `[A-1]`
- The ledger gives the lower bound of the validity range as inclusive and the upper bound as exclusive. The ledger makes this true. `[A-2]`
- All times in the datum and in the validity range are POSIX time in milliseconds. The app and the ledger make this true. `[A-3]`
- The backend builds each transaction with `L = now - 1 minute` and `U = now + 10 minutes`. The backend makes this true. `[A-4]`
- The app locks ADA only. The validator looks at lovelace only, so other assets would be ignored. `[A-5]`

## Definitions

- **deposit script**: the validator in `Deposit.scala`.
- **deposit input**: an input of the transaction whose address has the payment credential of the deposit script.
- **own input**: the input that `ownRef` references.
- **deposit**: the lovelace amount in the value of the own input. Other assets are ignored.
- **booking**: the `Booking` datum of the own input.
- **L**: the lower bound of the validity range, as a POSIX time in ms.
- **U**: the upper bound of the validity range, as a POSIX time in ms.
- **finite bound**: a bound that has a time value. An infinite bound has none.
- **notice period**: the constant 3_600_000 ms.
- **grace period**: the constant 21_600_000 ms.
- **minimum fee**: the constant 2_000_000 lovelace.
- **maximum bps**: the constant 5000.
- **fee**: the lovelace amount that the host receives on a late cancel, defined in `[CA-7]` to `[CA-12]`.
- **free cancel**: a Cancel with `U <= cancelBy`.
- **late cancel**: a Cancel that is not a free cancel and has `U <= checkIn - notice period`.
- **host output sum**: the sum of lovelace over all outputs of the transaction whose address has the payment key hash `host`. The staking part of the address is ignored.
- **guest output sum**: the same sum for the payment key hash `guest`.
- **signed by X**: the key hash X is in the signatories of the transaction.

## Common checks (all actions)

### Overview

*Informative.* The validator first reads the datum and the redeemer. It then makes sure that the transaction spends only one deposit. Then it runs the checks of the action that the redeemer names.

### Who

Any party that spends a deposit input. The ledger runs the validator.

### Trigger

The ledger spends an input at the deposit script.

### Structure

| Name | Type | Role |
|---|---|---|
| `datum` | `Option[Data]` | Must decode as `Booking` |
| `redeemer` | `Data` | Must decode as `Action` |
| `tx` | `TxInfo` | Inputs, outputs, signatories, validity range |
| `ownRef` | `TxOutRef` | Names the own input |

### Checks enforced here

- The deposit script MUST fail if `datum` is absent. `[SP-1]`
- The deposit script MUST fail if `datum` does not decode as `Booking`. `[SP-2]`
- The deposit script MUST fail if `redeemer` does not decode as `Action`. `[SP-3]`
- The deposit script MUST fail if `ownRef` matches no input of the transaction. `[SP-4]`
- The deposit script MUST fail unless the transaction has exactly one deposit input. `[SP-5]`
- The deposit script MUST apply the same rule `[SP-5]` to every action. `[SP-6]`
- The deposit script MUST compute the deposit from the lovelace of the own input only. `[SP-7]`
- The deposit script MUST succeed only if every check of the action in the redeemer holds. `[SP-8]`
- The deposit script MUST NOT apply any check that this specification does not list. `[SP-9]`

**Why:** `[SP-5]` stops double satisfaction. A host with two bookings from one guest could otherwise release both with one payment. `[SP-6]` covers all actions, because the same attack exists for each. `[SP-9]` keeps the validator small, and covers the stray cases that the notes closed, such as a datum with `cancelBy` later than `checkIn` minus the notice period.

*Example, illustrative.* A transaction that spends two deposit inputs fails. This holds even if both spend with `Release` and the guest receives both deposits.

### Checks delegated elsewhere

- The ledger MUST check the signatures of the keys in the signatories.
- The app MUST lock the deposit with a sound datum. See `[A-1]`.

## Cancel

### Overview

*Informative.* The guest cancels and takes the deposit back. A free cancel needs only the guest signature and a proven time. A late cancel also pays the host an exact fee. After the notice period before `checkIn`, Cancel is no longer possible.

### Who

The guest. The host MUST NOT cancel for the guest. The host uses Release.

### Trigger

The redeemer is `Cancel`.

### Structure

| Name | Type | Role |
|---|---|---|
| `booking.guest` | `PubKeyHash` | Must sign |
| `booking.cancelBy` | `PosixTime` | Limit of the free cancel |
| `booking.checkIn` | `PosixTime` | Base of the late cancel limit |
| `booking.feeBps` | `BigInt` | Rate of the fee, in basis points |
| U | finite bound | Must be finite |
| host output sum | lovelace | Must equal the fee on a late cancel |

### Checks enforced here

- The deposit script MUST fail unless the transaction is signed by `booking.guest`. `[CA-1]`
- The deposit script MUST fail if U is not a finite bound. `[CA-2]`
- The deposit script MUST ignore L. `[CA-3]`
- The deposit script MUST succeed with no output check if `U <= booking.cancelBy`. `[CA-4]`
- The deposit script MUST test the free cancel before the late cancel. `[CA-5]`
- The deposit script MUST fail if `U > booking.cancelBy` and `U > booking.checkIn - notice period`. `[CA-6]`
- The deposit script MUST compute the rate as `min(booking.feeBps, maximum bps)`. `[CA-7]`
- The deposit script MUST treat a negative `booking.feeBps` as 0. `[CA-8]`
- The deposit script MUST compute the raw fee as `ceil(deposit * rate / 10000)`. `[CA-9]`
- The deposit script MUST raise the raw fee to the minimum fee if it is lower. `[CA-10]`
- The deposit script MUST lower the fee to the deposit if it is higher than the deposit. `[CA-11]`
- The deposit script MUST apply `[CA-7]` to `[CA-11]` in this order. `[CA-12]`
- The deposit script MUST fail on a late cancel unless the host output sum equals the fee. `[CA-13]`
- The deposit script MUST NOT check the guest output sum on a late cancel. `[CA-14]`
- The deposit script MUST NOT check any output on a free cancel. `[CA-15]`

**Why:** Time is accepted only if the range proves it, so an infinite U fails (`[CA-2]`). The exact fee in `[CA-13]` stops a wallet bug that sends everything to the host. `[CA-11]` means the guest never loses more than the deposit, even if the deposit is below the minimum fee. `[CA-7]` and `[CA-8]` clamp a bad datum, because the validator cannot reject the booking at lock time, and Cancel must still work. Rounding up in `[CA-9]` favours the host. Cancel stays free for U equal to `cancelBy` (`[CA-4]`) and is tested first (`[CA-5]`), so a datum with `cancelBy` after the late limit still gives a free cancel.

*Example, illustrative.* Time, with `cancelBy = 1_000_000_000`, `checkIn = 1_100_000_000`. The late limit is `1_096_400_000`.

| U | Result |
|---|---|
| 1_000_000_000 | free cancel |
| 1_000_000_001 | late cancel, fee due |
| 1_096_400_000 | late cancel, fee due |
| 1_096_400_001 | fail |
| infinite | fail |

*Example, normative. Test vectors for `[CA-7]` to `[CA-12]`.*

| Deposit (lovelace) | `feeBps` | Fee (lovelace) |
|---|---|---|
| 25_000_001 | 1000 | 2_500_001 |
| 100_000_000 | 9000 | 50_000_000 |
| 100_000_000 | 0 | 2_000_000 |
| 10_000_000 | 1000 | 2_000_000 |
| 1_500_000 | 1000 | 1_500_000 |
| 1_500_000 | 0 | 1_500_000 |

Fee for the first row: `25_000_001 * 1000 / 10000 = 2_500_000.1`, rounded up to 2_500_001.

*Example, illustrative.* Late cancel with a fee of 2_500_001. Outputs to `host` of 2_000_000 and 500_001 pass. A single output of 2_500_002 fails. A single output of 2_500_000 fails.

### Checks delegated elsewhere

- The ledger MUST check the signature of the guest.
- The guest wallet decides where the rest of the deposit goes.

## Release

### Overview

*Informative.* The host gives the deposit back to the guest. This is for a guest who shows up, or for a host who is being kind. No clock applies, so the host can release at any time. The host may pay the guest more than the deposit.

### Who

The host. The guest MUST NOT release to themselves.

### Trigger

The redeemer is `Release`.

### Structure

| Name | Type | Role |
|---|---|---|
| `booking.host` | `PubKeyHash` | Must sign |
| `booking.guest` | `PubKeyHash` | Receives the deposit |
| guest output sum | lovelace | Must be at least the deposit |

### Checks enforced here

- The deposit script MUST fail unless the transaction is signed by `booking.host`. `[RE-1]`
- The deposit script MUST fail unless the guest output sum is at least the deposit. `[RE-2]`
- The deposit script MUST NOT check the validity range. `[RE-3]`
- The deposit script MUST NOT require the guest output sum to equal the deposit. `[RE-4]`

**Why:** Only the host signature makes Release safe. Otherwise the guest could release to themselves, and the deposit would have no point. `[RE-4]` lets the host add a refund for another reason. The same payment cannot settle two deposits, by `[SP-5]`.

*Example, illustrative.* Deposit of 100_000_000. Guest output sum of 100_000_000 passes. A sum of 120_000_000 passes. A sum of 99_999_999 fails.

### Checks delegated elsewhere

- The ledger MUST check the signature of the host.

## Claim

### Overview

*Informative.* The host keeps the deposit after a no-show. The guest has a grace period after `checkIn` to arrive. Only when L is later than the end of that period does the range prove a no-show. The host decides where the deposit goes.

### Who

The host.

### Trigger

The redeemer is `Claim`.

### Structure

| Name | Type | Role |
|---|---|---|
| `booking.host` | `PubKeyHash` | Must sign |
| `booking.checkIn` | `PosixTime` | Base of the grace period |
| L | finite bound | Must be finite |

### Checks enforced here

- The deposit script MUST fail unless the transaction is signed by `booking.host`. `[CL-1]`
- The deposit script MUST fail if L is not a finite bound. `[CL-2]`
- The deposit script MUST fail unless `L > booking.checkIn + grace period`. `[CL-3]`
- The deposit script MUST ignore U. `[CL-4]`
- The deposit script MUST NOT check any output. `[CL-5]`

**Why:** `[CL-3]` is strict, so the guest has the full grace period. The host cannot claim at exactly `checkIn + grace period`. An infinite lower bound proves nothing, so `[CL-2]` fails it.

*Example, illustrative.* With `checkIn = 1_100_000_000`, the limit is `1_121_600_000`.

| L | Result |
|---|---|
| 1_121_600_000 | fail |
| 1_121_600_001 | accept |
| infinite | fail |

### Checks delegated elsewhere

- The ledger MUST check the signature of the host.

## Open questions

*Informative.*

- Admin key or pause switch. Still open. Legal decides. v1 has none (see Non-goals).
- Negative `feeBps`. The notes do not say. Decided by `[CA-8]`.
- Whether the closure flag of a bound matters. The notes say L is inclusive and U is exclusive. The validator uses the time value only. See `[A-2]`, `[CA-4]`, `[CL-3]`.
- Deposit with no lovelace, or fee of 0 after `[CA-11]`. Decided by `[CA-13]`: the host output sum must then be 0.
- Whether host outputs at a script address count. Decided by the definition of host output sum: only payment key hash outputs count.
