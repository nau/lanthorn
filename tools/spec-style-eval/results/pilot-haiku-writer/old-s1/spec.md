# Deposit Validator Specification

- Version: 1
- Date: 2026-11-20
- Component: `Deposit` in `Deposit.scala`, package `validator`

## 1. Purpose and Scope

The Deposit validator holds the deposit that a guest locks for one booking.
It decides whether the host keeps the deposit, the host returns it, or the guest cancels and pays a fee.
It is a Plutus V3 spend validator written in Scalus.
The types and the entry point are fixed by `Deposit.scala`.

## 2. Conformance

Capitals are normative, per BCP 14.

Implementations cite a rule ID in code and tests as `// spec [ID]`.
Every example in this document is normative. Each example names the rules it tests.

Implementation status: None recorded.

## 3. Definitions

| Term | Meaning |
|---|---|
| Deposit validator | The object `Deposit` in `Deposit.scala`. |
| Booking | The datum, a value of type `Booking`. |
| Deposit UTxO | The UTxO at the Deposit validator address. It holds one Booking datum and one deposit. |
| Deposit | The lovelace value of the input referenced by `ownRef`. |
| Guest | The key hash `guest` of the Booking. |
| Host | The key hash `host` of the Booking. |
| Booking application | The off-chain application that builds the lock transaction. |
| Signatories | `tx.signatories`, the key hashes that signed the transaction. |
| Validity range | `tx.validRange`. |
| Lower bound L | The finite value of the lower bound of the validity range. |
| Upper bound U | The finite value of the upper bound of the validity range. |
| Absent bound | A bound of the validity range that has no finite value. |
| Payment key hash of an output | The key hash of the public-key payment credential of the output address. It is absent for a script address. |
| Guest output sum | The sum of lovelace over all outputs whose payment key hash equals `guest`. |
| Host output sum | The sum of lovelace over all outputs whose payment key hash equals `host`. |
| Cancel cutoff | The value `checkIn - 3_600_000`. |
| Free Cancel | A Cancel accepted under rule CN-4. |
| Late Cancel | A Cancel accepted under rule CN-5. |
| Fee | The lovelace amount computed by Section 7. |
| Lovelace | The smallest unit of ADA. |
| ADA | 1 ADA is 1_000_000 lovelace. |
| Basis point | 1/10_000. A `feeBps` of 1000 is 10%. |
| POSIX ms | Milliseconds since 1970-01-01T00:00:00Z. |

Constants. None of these values is in the datum.

| Name | Value | Used in |
|---|---|---|
| Fee cap | 5_000 basis points | FE-1 |
| Basis point denominator | 10_000 | FE-2 |
| Fee floor | 2_000_000 lovelace | FE-3 |
| Cancel notice | 3_600_000 ms (1 hour) | CN-5, CN-6 |
| Grace period | 21_600_000 ms (6 hours) | CL-3 |

**Why:** One term has one meaning in this document. The constants are fixed in code because the datum cannot change them per booking.

## 4. Interface

### 4.1 Datum and Redeemer

The `Booking` datum has these fields.

| Field | Type | Meaning |
|---|---|---|
| `guest` | `PubKeyHash` | Key hash of the guest. |
| `host` | `PubKeyHash` | Key hash of the host. |
| `cancelBy` | `PosixTime` | POSIX ms. Last time of a Free Cancel. |
| `checkIn` | `PosixTime` | POSIX ms. Start of the stay. |
| `feeBps` | `BigInt` | Fee rate in basis points. |

The `Action` redeemer has three constructors: `Cancel`, `Release`, `Claim`.

- [IF-1] The Deposit validator MUST use the `Booking` and `Action` types exactly as declared in `Deposit.scala`.

**Why:** The backend already serializes these types. A changed field makes every locked Deposit UTxO unspendable.

### 4.2 Spend Entry Point

| Name | Type | Use |
|---|---|---|
| `datum` | `Option[Data]` | Decoded as `Booking`. |
| `redeemer` | `Data` | Decoded as `Action`. |
| `tx` | `TxInfo` | Signatories, validity range, inputs, outputs. |
| `ownRef` | `TxOutRef` | Identifies the Deposit UTxO being spent. |
| Result | `Unit` | Accept or fail. |

- [IF-2] The Deposit validator MUST accept a spend by returning normally and MUST reject a spend by failing.

## 5. Spend Entry Checks

**Purpose:** Run the common checks on every Deposit spend.
**Who:** The ledger, for each input at the Deposit validator address.
**Trigger:** A transaction spends a Deposit UTxO with any `Action`.
**Structure:** Inputs as in Section 4.2. Result: accept or fail.

**Checks enforced here:**

- [SP-1] The Deposit validator MUST fail when the datum is absent.
- [SP-2] The Deposit validator MUST fail when the datum does not decode as `Booking`.
- [SP-3] The Deposit validator MUST fail when the redeemer does not decode as `Action`.
- [SP-4] The Deposit validator MUST fail unless exactly one input of `tx` has the address of the input referenced by `ownRef`.
- [SP-5] The Deposit validator MUST take the deposit as the lovelace of the input referenced by `ownRef`.
- [SP-6] The Deposit validator MUST take L and U as the finite values of `tx.validRange`.
- [SP-7] The Deposit validator MUST NOT adjust L or U for the bound types of the validity range.
- [SP-8] The Deposit validator MUST fail when a rule needs an absent bound.
- [SP-9] The Deposit validator MUST read no asset other than lovelace.
- [SP-10] The Deposit validator MUST accept a spend only when every check that applies to its `Action` passes.

**Checks delegated elsewhere:** None.

**Why:** SP-4 stops one payment from settling two deposits. A host with two bookings from one guest could otherwise release both with one payment. Batching is not part of version 1.

**Why:** SP-7 matters because the ledger gives L inclusive and U exclusive. The rules compare the values as written, so a Free Cancel with U equal to `cancelBy` is still free.

**Why:** SP-8 follows one rule of thumb. If the validity range does not prove a condition, the answer is no.

**Why:** SP-9 keeps the design ADA only. Other assets in a Deposit UTxO are not protected. An action that passes can move them.

## 6. Cancel

**Purpose:** Let the guest cancel the booking. The cancel is free until `cancelBy`. It carries a fee until the Cancel cutoff.
**Who:** Guest.
**Trigger:** A transaction spends a Deposit UTxO with redeemer `Cancel`.
**Structure:**

| Item | Use |
|---|---|
| `Booking.guest` | Signatories contain it (CN-1). |
| `Booking.cancelBy` | Free Cancel limit (CN-4). |
| `Booking.checkIn` | Cancel cutoff (CN-5, CN-6). |
| `Booking.feeBps` | Fee input (Section 7). |
| Upper bound U | Decides the branch. The lower bound L is not used. |
| Host output sum | Checked only in the Late Cancel branch. |
| Result | Accept or fail. |

**Checks enforced here:**

- [CN-1] The Deposit validator MUST accept Cancel only when the signatories contain `guest`.
- [CN-2] The Deposit validator MUST reject Cancel when the upper bound U is absent.
- [CN-3] The Deposit validator MUST decide Cancel from the upper bound U and MUST NOT use the lower bound L.
- [CN-4] When U is at most `cancelBy`, the Deposit validator MUST accept Cancel without checking any output.
- [CN-5] Otherwise, when U is at most `checkIn - 3_600_000`, the Deposit validator MUST accept Cancel only when the host output sum equals the fee.
- [CN-6] Otherwise, the Deposit validator MUST reject Cancel.
- [CN-7] The Deposit validator MUST NOT constrain any output in Cancel, except through the host output sum in CN-5.

**Checks delegated elsewhere:** None.

**Decision table** (normative; tests CN-2 to CN-6). `checkIn` is 1_800_000_000_000 and `cancelBy` is 1_790_000_000_000. The Cancel cutoff is 1_799_996_400_000. The lower bound L is any value.

| Upper bound U | Branch | Output condition | Result | Rule |
|---|---|---|---|---|
| absent | none | none | reject | CN-2 |
| 1_789_999_999_999 | Free Cancel | none | accept | CN-4 |
| 1_790_000_000_000 | Free Cancel | none | accept | CN-4 |
| 1_790_000_000_001 | Late Cancel | host output sum = fee | accept only if the sum matches | CN-5 |
| 1_799_996_400_000 | Late Cancel | host output sum = fee | accept only if the sum matches | CN-5 |
| 1_799_996_400_001 | none | none | reject | CN-6 |

**Precedence vector** (normative; tests CN-4 before CN-5). Use a datum with `cancelBy` = 1_799_999_000_000, which is later than the Cancel cutoff. With U = 1_799_998_000_000, the Cancel is a Free Cancel and is accepted without output checks.

**Late Cancel output vector** (normative; tests CN-5 and CN-7). The deposit is 25_000_001 lovelace and `feeBps` is 1000. The fee is 2_500_001 lovelace.

| Host outputs | Guest outputs | Result |
|---|---|---|
| One output of 2_500_001 | Any | accept |
| Two outputs of 1_000_000 and 1_500_001 | Any | accept |
| One output of 2_500_000 | Any | reject |
| One output of 2_500_002 | Any | reject |
| None | 25_000_001 | reject |

**Why:** The Cancel cutoff gives the host notice. After the cutoff the guest can no longer cancel. The stay then ends with a show-up, a Release, or a Claim.

**Why:** CN-4 comes before CN-5 on purpose. A datum with a `cancelBy` after the cutoff stays free until `cancelBy`. DL-3 stops the booking application from producing such a datum, but the script still accepts one.

**Why:** CN-5 requires an exact match. A wallet bug once sent everything to the host. An exact match rejects a transaction that overpays the host.

**Why:** CN-1 allows only the guest to cancel. The host has Release for the same outcome.

## 7. Fee

**Purpose:** Compute the fee of a Late Cancel.
**Who:** The Deposit validator.
**Trigger:** The Late Cancel branch, rule CN-5.
**Structure:** Inputs are the deposit (SP-5) and `Booking.feeBps`. The result is a fee in lovelace.

**Checks enforced here:**

- [FE-1] The Deposit validator MUST replace `feeBps` with the fee cap of 5_000 when `feeBps` is greater than 5_000.
- [FE-2] The Deposit validator MUST compute the raw fee as the mathematical ceiling of deposit × bps ÷ 10_000, where bps is the value after FE-1.
- [FE-3] The Deposit validator MUST replace the raw fee with the fee floor of 2_000_000 lovelace when the raw fee is below 2_000_000 lovelace.
- [FE-4] The Deposit validator MUST replace the fee with the deposit when the fee is greater than the deposit.
- [FE-5] The Deposit validator MUST apply FE-1, FE-2, FE-3, and FE-4 in this order.

**Checks delegated elsewhere:** None.

**Fee vectors** (normative; tests FE-1 to FE-5). Amounts are in lovelace.

| Deposit | feeBps | After FE-1 | Raw fee (FE-2) | After FE-3 | Fee (FE-4) | Rules exercised |
|---|---|---|---|---|---|---|
| 25_000_000 | 1000 | 1000 | 2_500_000 | 2_500_000 | 2_500_000 | FE-2 |
| 25_000_001 | 1000 | 1000 | 2_500_001 | 2_500_001 | 2_500_001 | FE-2, rounds up from 2_500_000.1 |
| 25_000_000 | 9000 | 5000 | 12_500_000 | 12_500_000 | 12_500_000 | FE-1 |
| 25_000_000 | 0 | 0 | 0 | 2_000_000 | 2_000_000 | FE-3 |
| 25_000_000 | -5 | -5 | -12_500 | 2_000_000 | 2_000_000 | FE-3, negative rate |
| 20_000_000 | 1000 | 1000 | 2_000_000 | 2_000_000 | 2_000_000 | FE-3, equal to floor |
| 20_000_000 | 999 | 999 | 1_998_000 | 2_000_000 | 2_000_000 | FE-3, just below floor |
| 10_000_000 | 1000 | 1000 | 1_000_000 | 2_000_000 | 2_000_000 | FE-3 |
| 1_500_000 | 1000 | 1000 | 150_000 | 2_000_000 | 1_500_000 | FE-3, FE-4, FE-5 |

**Why:** FE-2 rounds up because rounding favours the host. The fee cap in FE-1 keeps a Cancel working when the datum holds a bad rate. No check runs at lock time, so a bad rate reaches the spend. The fee cap is 5_000 because the legal limit is 50%.

**Why:** A negative `feeBps` is not clamped from below. Its raw fee is at most 0, so FE-3 sets the fee to the fee floor. This is the same result as `feeBps` of 0.

**Why:** FE-3 is the minimum fee for a Late Cancel, even when `feeBps` is 0. The fee floor is applied before FE-4. If FE-4 ran first, a deposit below 2 ADA would be raised above the deposit. The 1_500_000 row shows this order.

## 8. Release

**Purpose:** Let the host return the deposit to the guest.
**Who:** Host.
**Trigger:** A transaction spends a Deposit UTxO with redeemer `Release`.
**Structure:**

| Item | Use |
|---|---|
| `Booking.host` | Signatories contain it (RL-1). |
| Deposit | Minimum for the guest output sum (RL-3). |
| Guest output sum | Checked against the deposit (RL-3). |
| Validity range | Not checked (RL-2). |
| Result | Accept or fail. |

**Checks enforced here:**

- [RL-1] The Deposit validator MUST accept Release only when the signatories contain `host`.
- [RL-2] The Deposit validator MUST NOT check the validity range in Release.
- [RL-3] The Deposit validator MUST accept Release only when the guest output sum is at least the deposit.
- [RL-4] The Deposit validator MUST NOT constrain any output in Release, except through the guest output sum in RL-3.

**Checks delegated elsewhere:** None.

**Release vectors** (normative; tests RL-1 to RL-4). The deposit is 25_000_000 lovelace.

| Signatories | Guest outputs | Validity range | Result | Rule |
|---|---|---|---|---|
| host | 25_000_000 | any | accept | RL-3 |
| host | 24_999_999 | any | reject | RL-3 |
| host | 30_000_000 | any | accept | RL-3 |
| host | 10_000_000 and 15_000_000 | any | accept | RL-3 |
| host | 25_000_000 | absent | accept | RL-2 |
| guest only | 25_000_000 | any | reject | RL-1 |

**Why:** RL-1 means the guest cannot release a deposit to self. Otherwise the deposit would protect nothing.

**Why:** RL-3 is "at least", not "exactly". The guest may receive more. The host may add a refund for another reason.

**Why:** RL-2 means the host can return the deposit at any time. There is no deadline for Release.

## 9. Claim

**Purpose:** Let the host keep the deposit for a no-show, after the grace period.
**Who:** Host.
**Trigger:** A transaction spends a Deposit UTxO with redeemer `Claim`.
**Structure:**

| Item | Use |
|---|---|
| `Booking.host` | Signatories contain it (CL-1). |
| `Booking.checkIn` | Start of the grace period (CL-3). |
| Lower bound L | Present, and past the grace period (CL-2, CL-3). |
| Upper bound U | Not checked (CL-4). |
| Result | Accept or fail. |

**Checks enforced here:**

- [CL-1] The Deposit validator MUST accept Claim only when the signatories contain `host`.
- [CL-2] The Deposit validator MUST reject Claim when the lower bound L is absent.
- [CL-3] The Deposit validator MUST accept Claim only when L is greater than `checkIn + 21_600_000`.
- [CL-4] The Deposit validator MUST NOT check the upper bound U in Claim.
- [CL-5] The Deposit validator MUST NOT check any output in Claim.

**Checks delegated elsewhere:** None.

**Claim vectors** (normative; tests CL-2 to CL-4). The host signs every row. `checkIn` is 1_800_000_000_000, so `checkIn + 21_600_000` is 1_800_021_600_000.

| Lower bound L | Upper bound U | Result | Rule |
|---|---|---|---|
| absent | any | reject | CL-2 |
| 1_800_021_599_999 | absent | reject | CL-3 |
| 1_800_021_600_000 | any | reject | CL-3 |
| 1_800_021_600_001 | absent | accept | CL-3, CL-4 |

**Why:** The grace period is fixed in code, not in the datum, so all bookings share it. CL-3 is strict. At exactly six hours after `checkIn` the host still cannot claim.

**Why:** U does not matter for Claim. The host decides the outcome after the guest is late.

## 10. Lock Transaction

**Purpose:** Lock the deposit of one booking at the Deposit validator address.
**Who:** Booking application.
**Trigger:** A guest books a stay.
**Structure:** The output is one Deposit UTxO at the Deposit validator address. It holds one Booking datum and the deposit in lovelace.

**Checks enforced here:** None. The Deposit validator does not run when a deposit is locked.

**Checks delegated elsewhere:**

- [DL-1] The booking application MUST lock at least 10_000_000 lovelace in each Deposit UTxO.
- [DL-2] The booking application MUST set `feeBps` to at most 5_000 in each Booking datum.
- [DL-3] The booking application MUST set `cancelBy` to at most `checkIn - 3_600_000` in each Booking datum.
- [DL-4] The booking application MUST create one Deposit UTxO for each booking.
- [DL-5] The booking application MUST attach a Booking datum to each Deposit UTxO.

**Why:** No check runs at lock time, so the script cannot enforce DL-1 to DL-5. The booking application is trusted for them. DL-2 follows the legal limit of 50%. DL-3 keeps every booking's Free Cancel inside the Cancel cutoff.

## 11. Exclusions

- [EX-1] The Deposit validator MUST NOT contain an admin key or a pause switch in version 1.
- [EX-2] The Deposit validator MUST NOT allow Release to keep any part of a deposit for the host.
- [EX-3] The Deposit validator MUST NOT special-case a booking where `guest` equals `host`.

**Why:** EX-1 reflects an open question for legal review. Version 1 ships without an admin key or pause switch. Adding one requires a new version of this specification.

**Why:** EX-2 excludes partial release. RL-3 enforces the same limit. A host cannot keep a fixed part, such as the cost of damage, through Release.

**Why:** EX-3 means test bookings with the same guest and host follow the same rules as all other bookings.
