# Deposit Validator Specification

Capitals are normative, per BCP 14.

## 1. Overview

*Informative.* A guest books a stay and locks a deposit in one UTxO at the validator. The UTxO carries a Booking datum. The datum names the guest, the host, the cancellation deadline, the check-in time, and the fee rate. The deposit is the lovelace in that UTxO, so the datum holds no amount. The guest uses Cancel to get the deposit back, less any fee. Up to `cancelBy`, the cancellation is free. After `cancelBy`, and up to one hour before `checkIn`, the guest pays a fee to the host. The host uses Release to return the deposit to the guest at any time. The host uses Claim to keep the deposit when the guest does not show up. Claim is possible only after a six-hour grace period that starts at `checkIn`. The validator never reads the current time. It reads only the validity range of the transaction, so it accepts a time condition only when the range proves it. Each transaction may spend only one deposit, so one payment cannot settle two bookings. The validator runs no checks when the UTxO is locked. The application that builds the lock transaction is trusted for the booking values.

## 2. Goals and non-goals

*Informative.*

**Goals.**

- Let the host keep a deposit when the guest does not show up.
- Give the guest a free cancellation up to `cancelBy`, and a cancellation with a fee until one hour before `checkIn`.
- Let the host return the deposit to the guest at any time.
- Let each transaction settle at most one deposit.

**Why now.** Hosts lose income from guests who do not show up. Last year a wallet bug sent every output to the host. The fee is therefore exact, not a minimum.

**Non-goals.** The specification leaves out the following work. No reader builds it.

- Partial release. The host cannot keep part of a deposit, for example for damage.
- Batching. One transaction cannot spend two or more deposits.
- Checks at lock time. The validator does not run when a deposit is locked, so it does not check `cancelBy`, `checkIn`, or the deposit amount then.
- Deposits in assets other than ADA. The validator reads only lovelace.
- An admin key and a pause switch. See Open questions.
- Checks on the guest outputs of a Cancel, and on any output of a Claim.

## 3. Assumptions

- `[A-1]` The Cardano ledger includes a transaction only when the ledger time t satisfies L ≤ t < U, where L and U are its validity range bounds. The Cardano ledger enforces this.
- `[A-2]` The Cardano ledger verifies every signature of a transaction. Its signatories list only the key hashes whose signatures it verified. The Cardano ledger enforces this.
- `[A-3]` The application builds each locking transaction with a Booking datum whose `cancelBy` is at most `checkIn` minus one hour. The application builds each locking transaction with a deposit of at least 10 ADA. The application enforces both.
- `[A-4]` The backend builds each Cancel, Release, and Claim transaction with L = now − 1 minute and U = now + 10 minutes, where `now` is the backend clock at build time. The backend enforces this.

## 4. Definitions

- **validator**: the object `Deposit` declared in `Deposit.scala`.
- **Booking**: the datum type declared in `Deposit.scala`. Its fields are `guest`, `host`, `cancelBy`, `checkIn`, and `feeBps`.
- **Action**: the redeemer type declared in `Deposit.scala`. Its cases are `Cancel`, `Release`, and `Claim`.
- **guest**: the `guest` field of the Booking. It is a `PubKeyHash`.
- **host**: the `host` field of the Booking. It is a `PubKeyHash`.
- **deposit**: the lovelace of the input that `ownRef` names. Other assets in that input are not part of the deposit.
- **deposit input**: an input of the transaction whose address has the script credential of the validator.
- **POSIX time**: a time in milliseconds since the POSIX epoch. Its type is `PosixTime`. The fields `cancelBy` and `checkIn` are POSIX times.
- **validity range**: the interval of POSIX time that the transaction carries. It has a lower bound L and an upper bound U.
- **lower bound L**: the lower end of the validity range. It is inclusive. It may be absent.
- **upper bound U**: the upper end of the validity range. It is exclusive. It may be absent.
- **cancelBy**: the `cancelBy` field of the Booking. It is the deadline of free cancellation.
- **checkIn**: the `checkIn` field of the Booking.
- **cancel cutoff**: `checkIn` minus 3_600_000 ms, which is one hour. It is the latest upper bound U of a Cancel that is not free. The value is a constant. It is not in the datum.
- **free cancellation**: a Cancel whose upper bound U is at most `cancelBy`.
- **late cancellation**: a Cancel whose upper bound U is greater than `cancelBy` and at most the cancel cutoff.
- **feeBps**: the `feeBps` field of the Booking. Its unit is basis points. 10_000 is 100 %.
- **fee rate**: the value of `feeBps`, capped at 5000, as set by `[CAN-6]`.
- **fee**: the lovelace that the guest pays to the host in a late cancellation. It is computed by `[CAN-6]`, `[CAN-7]`, `[CAN-8]`, and `[CAN-9]`.
- **host output**: an output of the transaction whose address has a public key payment credential equal to `host`. The staking part of the address is ignored.
- **guest output**: an output of the transaction whose address has a public key payment credential equal to `guest`. The staking part of the address is ignored.
- **host sum**: the sum of the lovelace of all host outputs.
- **guest sum**: the sum of the lovelace of all guest outputs.
- **grace period**: 6 hours, which is 21_600_000 ms, after `checkIn`. The value is a constant. It is not in the datum.

## 5. Checks for every action

*Informative.* The validator runs the checks of this section for every transaction. It then runs the checks of the operation named by the redeemer.

- `[GEN-1]` The validator MUST reject a transaction when any check of its action fails.
- `[GEN-2]` The validator MUST reject a transaction whose datum is missing.
- `[GEN-3]` The validator MUST accept a transaction only when it has exactly one deposit input.
- `[GEN-4]` The validator MUST take the deposit from the lovelace of the input that `ownRef` names.
- `[GEN-5]` The validator MUST ignore every asset other than lovelace in the inputs and outputs of the transaction.
- `[GEN-6]` The validator MUST keep the types `Booking` and `Action`, and the signature of `spend`, as declared in `Deposit.scala`.
- `[GEN-7]` The validator MUST treat a time condition as not proven when the validity range lacks the bound that the condition needs.
- `[GEN-8]` The validator MUST apply the same checks when `guest` and `host` are the same key hash.

**Why:** `[GEN-3]` keeps one payment from settling two deposits. Without it, a host with two bookings from one guest could release both with one payment and keep the second deposit. `[GEN-4]` and `[GEN-5]` make the deposit the lovelace of the UTxO, so the datum has one field less to get wrong. `[GEN-6]` keeps the types the backend already serializes. `[GEN-7]` gives the rule that the range proves a condition or the transaction fails. `[GEN-8]` gives no special case for test bookings.

*Example, illustrative.* A host holds two bookings with one guest. The host builds one transaction that spends both deposits and pays 100 ADA once. The validator rejects the transaction under `[GEN-3]`, because it has two deposit inputs.

## 6. Cancel

*Informative.* The guest cancels the booking and recovers the deposit. A Cancel up to `cancelBy` is free, and the validator checks no output. A Cancel after `cancelBy` and up to the cancel cutoff keeps a fee for the host. The fee is computed from the deposit and `feeBps`. After the cancel cutoff, the validator rejects every Cancel. The guest then either checks in or forfeits the deposit.

### Who

The guest.

### Trigger

A transaction spends the deposit input with redeemer `Cancel`.

### Structure

| Name | Type | Meaning |
|---|---|---|
| datum | `Booking` | Booking terms of the deposit input. |
| redeemer | `Action.Cancel` | The action. |
| deposit | lovelace, integer | Lovelace of the input that `ownRef` names. |
| validity range | interval of `PosixTime` | Lower bound L and upper bound U. |
| signatories | key hashes | Key hashes whose signatures the ledger verified. |
| outputs | list of outputs | Outputs of the transaction. |
| result | `Unit` | Returns normally to accept. Fails to reject. |

### Checks enforced here

- `[CAN-1]` The validator MUST reject a Cancel unless the transaction is signed by `guest`. `[A-2]`
- `[CAN-2]` The validator MUST reject a Cancel whose validity range has no upper bound U. `[A-1]`
- `[CAN-3]` The validator MUST first test whether a Cancel is a free cancellation, with U at most `cancelBy`.
- `[CAN-4]` The validator MUST accept a free cancellation without computing the fee and without checking any output.
- `[CAN-5]` The validator MUST reject a late cancellation whose upper bound U is greater than the cancel cutoff.
- `[CAN-6]` The validator MUST set the fee rate to 5000 when `feeBps` is greater than 5000, and to `feeBps` otherwise.
- `[CAN-7]` The validator MUST compute the fee as the ceiling of deposit × fee rate ÷ 10_000, in exact integer arithmetic.
- `[CAN-8]` The validator MUST raise the fee to 2_000_000 when the fee is less than 2_000_000.
- `[CAN-9]` The validator MUST lower the fee to the deposit when the fee is greater than the deposit, as the last fee step.
- `[CAN-10]` The validator MUST reject a late cancellation unless the host sum equals the fee exactly.
- `[CAN-11]` The validator MUST NOT check any guest output of a Cancel.

**Why:** `[CAN-1]` makes the guest the only signer of a Cancel. The host has Release for the same purpose. `[CAN-3]` and `[CAN-4]` make the free window win. A cancellation at or before `cancelBy` is free even when `cancelBy` is later than the cancel cutoff. `[CAN-4]` also means that a free cancellation works with a bad datum, because no fee is computed. `[CAN-5]` ends the window one hour before `checkIn`, so that the host gets notice. `[CAN-6]` caps the rate at 50 %, the legal maximum. The script cannot reject a booking at lock time, so the cap is applied at spend time. `[CAN-8]` and `[CAN-9]` set a minimum late fee of 2 ADA. The host output is not worth sending below that. The cap keeps the guest from losing more than is locked. The steps run in the order of their IDs. The floor applies to the rounded fee. The cap applies last, so a deposit under 2 ADA gives a fee equal to the deposit. `[CAN-10]` makes the host output exact. An overpayment to the host must fail. `[CAN-11]` leaves the guest's part of the transaction to the guest.

**Checks delegated elsewhere:** The Cardano ledger verifies the signatures and enforces the validity range, as stated in `[A-1]` and `[A-2]`. The backend sets the bounds, as stated in `[A-4]`.

*Example, illustrative.* Fee results. The rules win if they disagree with a row.

| Deposit (lovelace) | feeBps | Rate after `[CAN-6]` | Rounded fee `[CAN-7]` | Fee after `[CAN-8]` and `[CAN-9]` |
|---|---|---|---|---|
| 25_000_001 | 1000 | 1000 | 2_500_001 | 2_500_001 |
| 25_000_001 | 9000 | 5000 | 12_500_001 | 12_500_001 |
| 100_000_000 | 1000 | 1000 | 10_000_000 | 10_000_000 |
| 10_000_000 | 0 | 0 | 0 | 2_000_000 |
| 1_500_000 | 0 | 0 | 0 | 1_500_000 |

*Example, illustrative.* Upper bounds for a Cancel. The Booking has `cancelBy` = 1_700_000_000_000 and `checkIn` = 1_700_100_000_000. So the cancel cutoff is 1_700_096_400_000.

| Upper bound U | Result |
|---|---|
| absent | Reject, `[CAN-2]` |
| 1_700_000_000_000 | Accept as free cancellation, no output check, `[CAN-3]`, `[CAN-4]` |
| 1_700_000_000_001 | Accept only if the host sum equals the fee, `[CAN-5]`, `[CAN-10]` |
| 1_700_096_400_000 | Accept only if the host sum equals the fee, `[CAN-5]`, `[CAN-10]` |
| 1_700_096_400_001 | Reject, `[CAN-5]` |

*Example, illustrative.* A Booking with `cancelBy` = 1_700_098_000_000 and `checkIn` = 1_700_100_000_000. Its `cancelBy` is later than the cancel cutoff of 1_700_096_400_000.

| Upper bound U | Result |
|---|---|
| 1_700_098_000_000 | Accept as free cancellation, `[CAN-3]`, `[CAN-4]` |
| 1_700_098_000_001 | Reject, `[CAN-5]` |

## 7. Release

*Informative.* The host returns the deposit to the guest. The host signs the transaction, and the validator checks that the guest sum is at least the deposit. The validator reads no time. So the host may release before `cancelBy`, during the stay, or a year later. The host may add other outputs, for example a refund for something else.

### Who

The host.

### Trigger

A transaction spends the deposit input with redeemer `Release`.

### Structure

| Name | Type | Meaning |
|---|---|---|
| datum | `Booking` | Booking terms of the deposit input. |
| redeemer | `Action.Release` | The action. |
| deposit | lovelace, integer | Lovelace of the input that `ownRef` names. |
| signatories | key hashes | Key hashes whose signatures the ledger verified. |
| outputs | list of outputs | Outputs of the transaction. |
| result | `Unit` | Returns normally to accept. Fails to reject. |

### Checks enforced here

- `[REL-1]` The validator MUST reject a Release unless the transaction is signed by `host`. `[A-2]`
- `[REL-2]` The validator MUST NOT check any time condition of a Release.
- `[REL-3]` The validator MUST reject a Release unless the guest sum is at least the deposit.
- `[REL-4]` The validator MUST NOT limit any output of a Release, other than through `[REL-3]`.

**Why:** `[REL-1]` gives the host the only signature. The guest cannot release the deposit to themselves, or the deposit would protect nothing. `[REL-3]` is an at-least rule, not an exact rule. The guest may receive more, and the host may add a refund for something else.

*Example, illustrative.* The deposit is 25_000_001 lovelace.

| Guest outputs (lovelace) | Result |
|---|---|
| 25_000_001, one output | Accept, `[REL-3]` |
| 25_000_000, one output | Reject, `[REL-3]` |
| 20_000_000 and 5_000_001, two outputs with the same `guest` key hash | Accept, the sum counts, `[REL-3]` |
| 25_000_001, with a stake part that differs from the Booking | Accept, the staking part is ignored, `[REL-3]` |

**Checks delegated elsewhere:** The Cardano ledger verifies the signature, as stated in `[A-2]`.

## 8. Claim

*Informative.* The host takes the deposit when the guest does not show up. The validator accepts a Claim only after the grace period. The lower bound L is later than `checkIn` plus the grace period. The host signs, and the validator checks no output, so the host decides where the money goes. The validator does not read the upper bound.

### Who

The host.

### Trigger

A transaction spends the deposit input with redeemer `Claim`.

### Structure

| Name | Type | Meaning |
|---|---|---|
| datum | `Booking` | Booking terms of the deposit input. |
| redeemer | `Action.Claim` | The action. |
| deposit | lovelace, integer | Lovelace of the input that `ownRef` names. |
| validity range | interval of `PosixTime` | Lower bound L is read. Upper bound U is not read. |
| signatories | key hashes | Key hashes whose signatures the ledger verified. |
| outputs | list of outputs | Outputs of the transaction. |
| result | `Unit` | Returns normally to accept. Fails to reject. |

### Checks enforced here

- `[CLM-1]` The validator MUST reject a Claim unless the transaction is signed by `host`. `[A-2]`
- `[CLM-2]` The validator MUST reject a Claim unless the lower bound L is strictly greater than `checkIn` plus 21_600_000. `[A-1]`
- `[CLM-3]` The validator MUST NOT use the upper bound U of a Claim.
- `[CLM-4]` The validator MUST NOT check any output of a Claim.

**Why:** `[CLM-2]` gives the guest time to arrive late. The grace period was first proposed as 4 hours and was set to 6 hours. The bound is strict. At exactly `checkIn` plus 6 hours the guest still has the full window, so the host cannot claim yet. An absent lower bound is rejected under `[GEN-7]`. `[CLM-3]` holds because only the lower bound proves that the time has passed. `[CLM-4]` leaves the choice of payee to the host.

*Example, illustrative.* The Booking has `checkIn` = 1_700_100_000_000. So the grace period ends at 1_700_121_600_000.

| Lower bound L | Upper bound U | Result |
|---|---|---|
| absent | any | Reject, `[GEN-7]` |
| 1_700_121_600_000 | absent | Reject, `[CLM-2]` |
| 1_700_121_600_001 | absent | Accept, `[CLM-2]`, `[CLM-3]` |

**Checks delegated elsewhere:** The Cardano ledger verifies the signature, as stated in `[A-2]`. The backend sets the lower bound, as stated in `[A-4]`.

## 9. Open questions

*Informative.* Each point below is undecided in the source. Each one cites the rule that decides it, or says that it is still open.

- **Admin key or pause switch.** Still open. The team will ask legal. Until legal answers, the validator has no admin key and no pause switch. See the non-goals in Section 2.
- **Datum that is present but does not decode as a Booking.** Still open. `[GEN-1]` rejects such a transaction by default. The source does not say whether a Cancel must still work with it.
- **Negative `feeBps`.** The source does not state this case. `[CAN-6]` to `[CAN-9]` give a fee of 2_000_000, capped at the deposit. The team should confirm whether a negative value can occur.
