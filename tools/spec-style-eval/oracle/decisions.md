# Decisions of the oracle

Each row is one decision that `notes.md` makes. The "Note" column gives the chat line that
decides it. The "Tests" column lists the hidden tests that check it. A hidden test with no
row here must not exist.

"Stock" says what the Scalus examples and most tutorials do. A decision that differs from
stock cannot be guessed from memory. 11 of the 19 decisions differ.

| ID | Decision | Note | Stock | Tests |
|----|----------|------|-------|-------|
| D-1 | The deposit is the lovelace of the spent UTxO. The datum holds no amount. | Tue 10:05 | Differs: stock keeps the amount in the datum | S2, S6, S7, I5 |
| D-2 | Cancel needs the guest's signature. | Wed 09:21 | Same | S1 |
| D-3 | Cancel needs a finite upper bound `U`. | Tue 10:15 | Same | I1 |
| D-4 | Cancel is free when `U <= cancelBy`. | Tue 10:18 | Same | B1, I4 |
| D-5 | Late Cancel is valid when `U <= checkIn - 1 h`. After that, Cancel fails. | Tue 10:29 | Differs: a fixed lockout before the deadline | B2 |
| D-6 | A fee rate above 5000 bps counts as 5000. | Tue 11:50 | Differs: stock has no clamp | S8, B4, I6 |
| D-7 | The fee rounds up. | Tue 11:41, 11:43 | Differs: stock rounds down | S6, B5 |
| D-8 | The fee is at least 2 ADA, also at a rate of 0. | Tue 11:52 | Differs: stock has no minimum | S7, I6, I7 |
| D-9 | The fee never exceeds the deposit. The order is clamp, round up, minimum, cap. | Tue 11:54, 11:58 | Differs | I5, I6 |
| D-10 | On a late Cancel the host receives exactly the fee: the lovelace sum over all outputs to the host's payment key. | Wed 09:16 | Differs: stock checks "at least" | S2, B5, I8 |
| D-11 | Cancel checks nothing else: no output check when free, no check of the guest's part when late. | Wed 09:18, 09:19 | Differs: stock checks the refund | I8 |
| D-12 | Release needs the host's signature. | Wed 09:36 | Same | S3 |
| D-13 | Release has no time check. | Wed 09:34 | Differs: stock paths check a deadline | I3 |
| D-14 | On Release the guest receives at least the deposit. | Wed 09:32 | Same | S4, I3 |
| D-15 | Claim needs the host's signature. | Wed 14:05 | Same | S5 |
| D-16 | Claim is valid when `L > checkIn + 6 h`, strictly. | Wed 14:12 | Differs: stock uses `>=` and no grace period | B3 |
| D-17 | A transaction spends exactly one deposit input, for every action. | Thu 08:52 | Same in Scalus examples, often left out | I9 |
| D-18 | Claim checks no output. | Wed 14:14 | Same | I8 |
| D-19 | A time that the validity range does not prove is a rejection. So Claim needs a finite `L`. | Tue 10:15 | Same | I1, I2 |

## Test groups

| Group | Tests | What it holds |
|-------|-------|---------------|
| stated | S1 to S8 | One rule each, with values far from any limit. |
| boundary | B1 to B5 | The value at a limit and the value 1 next to it. |
| intent | I1 to I9 | Cases that a rule list may not spell out: a principle applied to a second action (I2, I3), two rules that interact (I5, I6, I7), things the validator must not check (I8), a rule stated for one action and tested on another (I9), and a wrong reading that the rule excludes (I1, I4). |

## Points that the notes leave open

No hidden test checks these: native tokens in the UTxO, a partial release, the case
`host == guest`, an admin key, a datum where `cancelBy` is later than `checkIn - 1 h`, and
what the lock transaction looks like.
