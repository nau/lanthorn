# Specification: no-show deposit validator (v1)

Capitals are normative, per BCP 14.

The implementer builds `Deposit.spend` in `Deposit.scala`. The types `Booking` and `Action` are frozen. Do not change them.

## 1. Overview

A guest books a stay and locks a deposit at the validator. One UTxO holds one booking.

- If the guest shows up, the host gives the deposit back (Release).
- If the guest does not show up, the host keeps the deposit (Claim).
- The guest can cancel (Cancel), free before `cancelBy` and with a fee until one hour before check-in.

## 2. Definitions

| Term | Meaning |
|---|---|
| validator | The `Deposit.spend` function. |
| booking | The `Booking` datum of the UTxO that is spent. |
| guest, host | The key hashes `booking.guest` and `booking.host`. |
| `cancelBy`, `checkIn`, `feeBps` | The fields of the booking. Times are POSIX ms. |
| own input | The input of the transaction whose output reference equals `ownRef`. |
| deposit | The lovelace amount of the value of the own input. It is not stored in the booking. |
| validity range | The field `tx.validRange` of the transaction. |
| L | The lower time of the validity range, as defined in 2.1. |
| U | The upper time of the validity range, as defined in 2.1. |
| fee | The lovelace amount computed by section 7. |
| paid to X | The sum of lovelace over all outputs of the transaction whose address has the payment credential `PubKeyCredential(X)`. The staking part of the address is ignored. |
| fail | The validator terminates with an error. The transaction is invalid. |

### 2.1 L and U

- L is absent when the lower bound of the validity range is not finite.
- L is the bound value when the bound is finite and inclusive.
- L is the bound value plus 1 when the bound is finite and exclusive.
- U is absent when the upper bound of the validity range is not finite.
- U is the bound value when the bound is finite and exclusive.
- U is the bound value plus 1 when the bound is finite and inclusive.

**Why:** The script never sees the current time. It sees only the validity range. The ledger gives L inclusive and U exclusive. The rules above give the same L and U for any equivalent form of the range. An absent L or U proves nothing about time.

### 2.2 Constants

| Name | Value |
|---|---|
| `LATE_CUTOFF` | 3_600_000 (1 hour in ms) |
| `GRACE` | 21_600_000 (6 hours in ms) |
| `MIN_FEE` | 2_000_000 lovelace (2 ADA) |
| `MAX_BPS` | 5000 |
| `BPS_BASE` | 10000 |

The constants are fixed in the validator. They are not in the booking.

## 3. General checks

These checks apply to every action. The validator MUST run them before it applies the checks of an action.

- `[GEN-1]` The validator MUST fail when `datum` is `None`.
- `[GEN-2]` The validator MUST fail when `datum` does not decode as `Booking`.
- `[GEN-3]` The validator MUST fail when `redeemer` does not decode as `Action`.
- `[GEN-4]` The validator MUST fail when `ownRef` matches no input of the transaction.
- `[GEN-5]` The validator MUST fail when the address of the own input has no script credential.
- `[GEN-6]` The validator MUST fail unless exactly one input of the transaction has an address with the same script credential as the own input.
- `[GEN-7]` The validator MUST NOT check any field of the booking beyond decoding. It MUST NOT check `cancelBy < checkIn`, the host key hash, the guest key hash, or the sign of `feeBps`.
- `[GEN-8]` The validator MUST count only lovelace as value. It MUST ignore every other asset.
- `[GEN-9]` The validator MUST accept the transaction only when the checks of the selected action all hold.

**Why:**

- `[GEN-6]` stops double satisfaction. A host with two bookings of one guest could release both with one payment, and keep the other deposit. One payment can settle only one deposit when each transaction spends one deposit. Batching is not supported in v1.
- `[GEN-7]` exists because nothing runs at lock time. The app builds the lock transaction and the validator trusts it. A bad booking MUST NOT trap the guest, so Cancel works with any decodable booking.

*Example, illustrative.* A transaction spends two UTxOs from the validator. Each UTxO is a different booking of one guest. The validator fails with `[GEN-6]` for both, whatever the action.

*Example, illustrative.* The host equals the guest, for a test booking. The validator has no special case. All rules apply as written.

## 4. Operation: Cancel

**Purpose:** The guest ends the booking and takes back the deposit, with or without a fee.

**Who:** The guest.

**Trigger:** The redeemer is `Cancel`.

**Structure:**

| Item | Type | Source |
|---|---|---|
| guest signature | key hash in `tx.signatories` | transaction |
| U | POSIX ms or absent | validity range |
| `cancelBy`, `checkIn` | POSIX ms | booking |
| fee | lovelace | section 7 |
| paid to host | lovelace | outputs of the transaction |

**Checks enforced here:**

- `[CAN-1]` The validator MUST fail unless the guest key hash is in `tx.signatories`.
- `[CAN-2]` The validator MUST fail when U is absent.
- `[CAN-3]` The validator MUST accept as a free cancel when U <= `cancelBy`. It MUST NOT check any output.
- `[CAN-4]` The validator MUST treat the cancel as late when U > `cancelBy` and U <= `checkIn` - `LATE_CUTOFF`.
- `[CAN-5]` The validator MUST fail when U > `cancelBy` and U > `checkIn` - `LATE_CUTOFF`.
- `[CAN-6]` For a late cancel, the validator MUST fail unless the amount paid to the host equals the fee exactly.
- `[CAN-7]` The validator MUST NOT check the lower bound L for Cancel. L can be any value or absent.
- `[CAN-8]` The validator MUST NOT check where the rest of the deposit goes.
- `[CAN-9]` The validator MUST apply `[CAN-3]` first. `cancelBy` later than `checkIn` - `LATE_CUTOFF` does not shorten the free window.
- `[CAN-10]` The host MUST NOT be able to cancel for the guest. The host signature alone does not satisfy `[CAN-1]`.

**Why:**

- `[CAN-6]` is exact, not a minimum. A wallet bug once sent everything to the host. A transaction that overpays the host MUST fail.
- `[CAN-8]` leaves the guest part free. The guest signs, so change and other wallets are the guest's business.
- `[CAN-2]` follows the rule that the range must prove the answer. No upper bound proves nothing.
- The app sets L to now minus 1 minute and U to now plus 10 minutes. This does not change any rule.
- `[CAN-5]` ends Cancel for good. After that the guest shows up or loses the deposit.
- Cancel needs the host to have notice. So the last hour before check-in has no cancel, unless `cancelBy` allows it by `[CAN-9]`.

*Example, normative, tests `[CAN-2]` to `[CAN-5]`.* With `cancelBy` = 1_000_000_000_000 and `checkIn` = 1_000_086_400_000. `checkIn` - `LATE_CUTOFF` is 1_000_082_800_000.

| U | Result |
|---|---|
| absent | fail |
| 1_000_000_000_000 | accept, free |
| 1_000_000_000_001 | late, fee applies |
| 1_000_082_800_000 | late, fee applies |
| 1_000_082_800_001 | fail |

*Example, normative, tests `[CAN-9]`.* With `cancelBy` = 1_000_090_000_000 and `checkIn` = 1_000_086_400_000. U = 1_000_090_000_000 is a free cancel. U = 1_000_090_000_001 fails.

**Checks delegated elsewhere:** None.

## 5. Operation: Release

**Purpose:** The host gives the deposit back to the guest.

**Who:** The host.

**Trigger:** The redeemer is `Release`.

**Structure:**

| Item | Type | Source |
|---|---|---|
| host signature | key hash in `tx.signatories` | transaction |
| deposit | lovelace | own input |
| paid to guest | lovelace | outputs of the transaction |

**Checks enforced here:**

- `[REL-1]` The validator MUST fail unless the host key hash is in `tx.signatories`.
- `[REL-2]` The validator MUST fail unless the amount paid to the guest is at least the deposit.
- `[REL-3]` The validator MUST NOT check the validity range for Release.
- `[REL-4]` The validator MUST NOT require the guest signature for Release.
- `[REL-5]` The validator MUST accept an amount paid to the guest above the deposit.

**Why:**

- `[REL-3]`: The host can always give the money back. It can do so before `cancelBy`, during the stay, or a year later.
- `[REL-4]`: If the guest could release to themselves, the deposit would have no purpose.
- `[REL-5]`: The host may add a refund for another reason. Partial release is not in v1.

*Example, illustrative.* The deposit is 100_000_000. Outputs to the guest total 100_000_000: accept. Total 120_000_000: accept. Total 99_999_999: fail.

**Checks delegated elsewhere:** None.

## 6. Operation: Claim

**Purpose:** The host keeps the deposit after a no-show.

**Who:** The host.

**Trigger:** The redeemer is `Claim`.

**Structure:**

| Item | Type | Source |
|---|---|---|
| host signature | key hash in `tx.signatories` | transaction |
| L | POSIX ms or absent | validity range |
| `checkIn` | POSIX ms | booking |

**Checks enforced here:**

- `[CLM-1]` The validator MUST fail unless the host key hash is in `tx.signatories`.
- `[CLM-2]` The validator MUST fail when L is absent.
- `[CLM-3]` The validator MUST fail unless L > `checkIn` + `GRACE`.
- `[CLM-4]` The validator MUST NOT check U for Claim.
- `[CLM-5]` The validator MUST NOT check any output for Claim.

**Why:**

- `[CLM-3]` is strict. The guest has the full grace period. At exactly `checkIn` + `GRACE` the host cannot claim.
- `[CLM-5]`: The host signs and decides where the deposit goes.

*Example, normative, tests `[CLM-2]` and `[CLM-3]`.* With `checkIn` = 1_000_000_000_000. `checkIn` + `GRACE` is 1_000_021_600_000.

| L | Result |
|---|---|
| absent | fail |
| 1_000_021_600_000 | fail |
| 1_000_021_600_001 | accept |

**Checks delegated elsewhere:** None.

## 7. Fee computation

The validator computes the fee only for a late cancel. It uses integer arithmetic on the deposit and on `feeBps`. It MUST apply these steps in this order.

- `[FEE-1]` The validator MUST set `bps` to the smaller of `feeBps` and `MAX_BPS`.
- `[FEE-2]` The validator MUST set `raw` to `deposit` * `bps` divided by `BPS_BASE`, rounded up to the next integer.
- `[FEE-3]` The validator MUST set `floored` to the larger of `raw` and `MIN_FEE`.
- `[FEE-4]` The validator MUST set the fee to the smaller of `floored` and `deposit`.
- `[FEE-5]` The validator MUST NOT fail because `feeBps` is above `MAX_BPS`. It MUST clamp it by `[FEE-1]`.
- `[FEE-6]` A negative `feeBps` MUST NOT make the validator fail. The steps `[FEE-1]` to `[FEE-4]` apply unchanged.

**Why:**

- Rounding up favours the host.
- Legal sets the maximum fee at 50 percent. The script cannot reject a booking at lock time, so it clamps.
- Under 2 ADA, a host output is not worth sending. So a late cancel always costs at least `MIN_FEE`, even when `feeBps` is 0.
- The app never locks less than 10 ADA, but anyone can lock by hand. `[FEE-4]` is last so that the guest never loses more than the locked amount.

*Example, normative, tests `[FEE-1]` to `[FEE-4]`.*

| deposit | `feeBps` | `bps` | `raw` | `floored` | fee |
|---|---|---|---|---|---|
| 25_000_001 | 1000 | 1000 | 2_500_001 | 2_500_001 | 2_500_001 |
| 100_000_000 | 0 | 0 | 0 | 2_000_000 | 2_000_000 |
| 100_000_000 | 9000 | 5000 | 50_000_000 | 50_000_000 | 50_000_000 |
| 1_500_000 | 1000 | 1000 | 150_000 | 2_000_000 | 1_500_000 |

Row 1: 25_000_001 * 1000 / 10000 is 2_500_000.1, so `raw` is 2_500_001.

**Checks delegated elsewhere:** None.

## 8. Not in v1

- The validator MUST NOT support more than one deposit input per transaction. See `[GEN-6]`.
- The validator MUST NOT support partial release.
- The validator MUST NOT have an admin key or a pause switch. Legal has not decided. A later version MAY add one.
- The validator MUST NOT check the lock transaction. The app builds it and the validator trusts it. This includes the lock of `cancelBy` before `checkIn`.

## 9. Open points and decisions of this spec

The source notes leave these points open. This spec decides them as follows.

- Undecodable datum or redeemer: fail. Rules `[GEN-2]` and `[GEN-3]`. The notes only say a missing datum fails.
- Inclusive upper bound, exclusive lower bound, or a non-finite bound: handled by 2.1. The notes only say the ledger gives L inclusive and U exclusive, and that an unproven answer is no.
- Own input not found, or not at a script address: fail. Rules `[GEN-4]` and `[GEN-5]`. The validator needs the script credential to count deposit inputs.
- Outputs paid to a script credential never count as paid to the host or the guest. They have no `PubKeyCredential`.
- Reference inputs are not inputs for `[GEN-6]`. Only `tx.inputs` counts.
- Negative `feeBps`: see `[FEE-6]`. The floor `MIN_FEE` then sets the fee.
