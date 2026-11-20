package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.{ByteString, Data}
import scalus.uplc.builtin.Data.toData
import scalus.cardano.onchain.plutus.prelude.{List => PList, Option}
import scalus.cardano.onchain.plutus.{v1, v2, v3}
import scalus.testing.kit.ScalusTest

/** Unit tests of `Deposit`. Each test builds a script context by hand, so the validity range is
  * exact in milliseconds, and runs the compiled validator on it. Every check is tagged with the
  * rule ID of SPEC.md.
  */
class DepositTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default
    private val contract = PlutusV3.compile(Deposit.validate)

    // ----- fixtures ---------------------------------------------------------------------------

    private def hex(n: Int, bytes: Int): ByteString =
        val digits = n.toHexString
        ByteString.fromHex("0" * (bytes * 2 - digits.length) + digits)

    private def pkh(n: Int): v1.PubKeyHash = v1.PubKeyHash(hex(n, 28))

    private val guest = pkh(1)
    private val host = pkh(2)
    private val scriptHash = hex(0xabc, 28)
    private val scriptAddress = v1.Address.fromCredential(
      v1.Credential.ScriptCredential(scriptHash)
    )

    private val cancelBy = BigInt(1_000_000_000L)
    private val checkIn = BigInt(1_100_000_000L)
    private val notice = BigInt(3_600_000L)
    private val grace = BigInt(21_600_000L)

    private def booking(feeBps: BigInt = 1000): Booking =
        Booking(
          guest = guest,
          host = host,
          cancelBy = cancelBy,
          checkIn = checkIn,
          feeBps = feeBps
        )

    private def refOf(index: Int): v3.TxOutRef =
        v3.TxOutRef(v3.TxId(hex(0x100 + index, 32)), BigInt(index))

    private def lockedInput(index: Int, deposit: BigInt): v3.TxInInfo =
        v3.TxInInfo(
          refOf(index),
          v2.TxOut(
            scriptAddress,
            v1.Value.lovelace(deposit),
            v2.OutputDatum.NoOutputDatum,
            Option.None
          )
        )

    private def payTo(pkh: v1.PubKeyHash, amount: BigInt): v2.TxOut =
        v2.TxOut(
          v1.Address.fromPubKeyHash(pkh),
          v1.Value.lovelace(amount),
          v2.OutputDatum.NoOutputDatum,
          Option.None
        )

    /** Interval helpers. `from` is inclusive and `to` is exclusive, as the ledger gives them. */
    private def window(from: BigInt, to: BigInt): v1.Interval =
        v1.Interval(
          v1.IntervalBound.finiteInclusive(from),
          v1.IntervalBound.finiteExclusive(to)
        )

    private def upTo(to: BigInt): v1.Interval =
        v1.Interval(v1.IntervalBound.negInf, v1.IntervalBound.finiteExclusive(to))

    private def from(lower: BigInt): v1.Interval =
        v1.Interval(v1.IntervalBound.finiteInclusive(lower), v1.IntervalBound.posInf)

    /** Runs the compiled validator on a transaction that spends `inputs`, with `ownRef` as the
      * input being validated.
      */
    private def run(
        datum: Option[Data],
        redeemer: Data,
        inputs: List[v3.TxInInfo],
        ownRef: v3.TxOutRef = refOf(0),
        signers: List[v1.PubKeyHash] = Nil,
        range: v1.Interval = v1.Interval.always,
        outputs: List[v2.TxOut] = Nil
    ): Boolean = {
        val tx = v3.TxInfo.placeholder.copy(
          inputs = PList.from(inputs),
          outputs = PList.from(outputs),
          signatories = PList.from(signers),
          validRange = range
        )
        val ctx = v3.ScriptContext(
          tx,
          redeemer,
          v3.ScriptInfo.SpendingScript(ownRef, datum)
        )
        contract.program.runWithDebug(ctx).isSuccess
    }

    /** One deposit input of `deposit` lovelace, validated with the datum of `b`. */
    private def single(
        b: Booking,
        redeemer: Data,
        deposit: BigInt = 100_000_000,
        signers: List[v1.PubKeyHash] = Nil,
        range: v1.Interval = v1.Interval.always,
        outputs: List[v2.TxOut] = Nil
    ): Boolean =
        run(
          Option.Some(b.toData),
          redeemer,
          List(lockedInput(0, deposit)),
          signers = signers,
          range = range,
          outputs = outputs
        )

    // ----- common checks ----------------------------------------------------------------------

    // spec [SP-1]
    test("[SP-1] fails when the datum is absent") {
        assert(
          !run(
            Option.None,
            Action.Release.toData,
            List(lockedInput(0, 100_000_000)),
            signers = List(host),
            outputs = List(payTo(guest, 100_000_000))
          )
        )
    }

    // spec [SP-2]
    test("[SP-2] fails when the datum does not decode as Booking") {
        assert(
          !run(
            Option.Some(host.toData),
            Action.Release.toData,
            List(lockedInput(0, 100_000_000)),
            signers = List(host),
            outputs = List(payTo(guest, 100_000_000))
          )
        )
    }

    // spec [SP-3]
    test("[SP-3] fails when the redeemer does not decode as Action") {
        assert(!single(booking(), BigInt(7).toData, signers = List(guest)))
    }

    // spec [SP-4]
    test("[SP-4] fails when ownRef matches no input") {
        assert(
          !run(
            Option.Some(booking().toData),
            Action.Release.toData,
            List(lockedInput(0, 100_000_000)),
            ownRef = refOf(9),
            signers = List(host),
            outputs = List(payTo(guest, 100_000_000))
          )
        )
    }

    // spec [SP-5]
    test("[SP-5] fails when the transaction spends two deposits, even if both are released") {
        assert(
          !run(
            Option.Some(booking().toData),
            Action.Release.toData,
            List(lockedInput(0, 100_000_000), lockedInput(1, 100_000_000)),
            signers = List(host),
            outputs = List(payTo(guest, 200_000_000))
          )
        )
    }

    // spec [SP-6]
    test("[SP-6] the one-deposit rule applies to Claim too") {
        assert(
          !run(
            Option.Some(booking().toData),
            Action.Claim.toData,
            List(lockedInput(0, 100_000_000), lockedInput(1, 100_000_000)),
            signers = List(host),
            range = from(checkIn + grace + 1)
        )
        )
    }

    // spec [SP-6]
    test("[SP-6] the one-deposit rule applies to Cancel too") {
        assert(
          !run(
            Option.Some(booking().toData),
            Action.Cancel.toData,
            List(lockedInput(0, 100_000_000), lockedInput(1, 100_000_000)),
            signers = List(guest),
            range = upTo(cancelBy)
        )
        )
    }

    // spec [SP-7]
    test("[SP-7] the deposit is the lovelace of the own input, not of other inputs") {
        // The own input holds 100 ADA. A second deposit input is not allowed by [SP-5], so use a
        // non-script input: the own input alone sets the deposit.
        val plainInput = v3.TxInInfo(
          refOf(1),
          v2.TxOut(
            v1.Address.fromPubKeyHash(guest),
            v1.Value.lovelace(500_000_000),
            v2.OutputDatum.NoOutputDatum,
            Option.None
          )
        )
        // Release needs guest output >= 100 ADA (own input). 60 ADA is short.
        assert(
          !run(
            Option.Some(booking().toData),
            Action.Release.toData,
            List(lockedInput(0, 100_000_000), plainInput),
            signers = List(host),
            outputs = List(payTo(guest, 60_000_000))
          )
        )
        // 100 ADA passes.
        assert(
          run(
            Option.Some(booking().toData),
            Action.Release.toData,
            List(lockedInput(0, 100_000_000), plainInput),
            signers = List(host),
            outputs = List(payTo(guest, 100_000_000))
          )
        )
    }

    // ----- Cancel -----------------------------------------------------------------------------

    // spec [CA-1]
    test("[CA-1] cancel fails unless the guest signs") {
        assert(!single(booking(), Action.Cancel.toData, signers = List(host), range = upTo(cancelBy)))
    }

    // spec [CA-2]
    test("[CA-2] cancel fails when U is infinite") {
        assert(
          !single(
            booking(),
            Action.Cancel.toData,
            signers = List(guest),
            range = from(0)
          )
        )
    }

    // spec [CA-3]
    test("[CA-3] cancel ignores L") {
        val range = v1.Interval(
          v1.IntervalBound.finiteInclusive(cancelBy + 999_999_999),
          v1.IntervalBound.finiteExclusive(cancelBy)
        )
        assert(single(booking(), Action.Cancel.toData, signers = List(guest), range = range))
    }

    // spec [CA-4] [CA-15]
    test("[CA-4] free cancel at U = cancelBy needs no output") {
        assert(
          single(booking(), Action.Cancel.toData, signers = List(guest), range = upTo(cancelBy))
        )
    }

    // spec [CA-5] [CA-15]
    test("[CA-5] free cancel is accepted with no output check, even if the host gets more") {
        assert(
          single(
            booking(),
            Action.Cancel.toData,
            signers = List(guest),
            range = upTo(cancelBy - 1),
            outputs = List(payTo(host, 100_000_000))
          )
        )
    }

    // spec [CA-6]
    test("[CA-6] cancel after checkIn - notice period fails") {
        val limit = checkIn - notice
        assert(
          !single(
            booking(),
            Action.Cancel.toData,
            signers = List(guest),
            range = upTo(limit + 1),
            outputs = List(payTo(host, 10_000_000))
          )
        )
    }

    // spec [CA-6] [CA-13]: boundary from the spec's time example, cancelBy = 1_000_000_000,
    // checkIn = 1_100_000_000
    test("[CA-6] late cancel is accepted at U = checkIn - notice period, the last moment") {
        val limit = checkIn - notice
        assert(limit == BigInt(1_096_400_000L))
        assert(
          single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(limit),
            outputs = List(payTo(host, 2_500_001))
          )
        )
    }

    // spec [CA-6]
    test("[CA-6] late cancel at U = cancelBy + 1 needs the fee") {
        assert(
          !single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(cancelBy + 1),
            outputs = Nil
          )
        )
        assert(
          single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(cancelBy + 1),
            outputs = List(payTo(host, 2_500_001))
          )
        )
    }

    // spec [CA-7] [CA-8] [CA-9] [CA-10] [CA-11] [CA-12]: the normative test vectors of SPEC.md.
    private val feeVectors: List[(BigInt, BigInt, BigInt)] = List(
      (BigInt(25_000_001L), BigInt(1000), BigInt(2_500_001L)),
      (BigInt(100_000_000L), BigInt(9000), BigInt(50_000_000L)),
      (BigInt(100_000_000L), BigInt(0), BigInt(2_000_000L)),
      (BigInt(10_000_000L), BigInt(1000), BigInt(2_000_000L)),
      (BigInt(1_500_000L), BigInt(1000), BigInt(1_500_000L)),
      (BigInt(1_500_000L), BigInt(0), BigInt(1_500_000L))
    )

    // spec [CA-7] [CA-8] [CA-9] [CA-10] [CA-11] [CA-12]
    test("[CA-7..CA-12] late cancel accepts exactly the normative fee of each vector") {
        val late = upTo(checkIn - notice)
        for (deposit, bps, fee) <- feeVectors do
            withClue(s"deposit=$deposit bps=$bps fee=$fee: ") {
                assert(
                  single(
                    booking(bps),
                    Action.Cancel.toData,
                    deposit = deposit,
                    signers = List(guest),
                    range = late,
                    outputs = List(payTo(host, fee))
                  )
                )
                assert(
                  !single(
                    booking(bps),
                    Action.Cancel.toData,
                    deposit = deposit,
                    signers = List(guest),
                    range = late,
                    outputs = List(payTo(host, fee + 1))
                  )
                )
                assert(
                  !single(
                    booking(bps),
                    Action.Cancel.toData,
                    deposit = deposit,
                    signers = List(guest),
                    range = late,
                    outputs = List(payTo(host, fee - 1))
                  )
                )
            }
    }

    // spec [CA-8]
    test("[CA-8] a negative feeBps is treated as 0, so the fee is the minimum") {
        val late = upTo(checkIn - notice)
        assert(
          single(
            booking(-5),
            Action.Cancel.toData,
            deposit = 100_000_000,
            signers = List(guest),
            range = late,
            outputs = List(payTo(host, 2_000_000))
          )
        )
        assert(
          !single(
            booking(-5),
            Action.Cancel.toData,
            deposit = 100_000_000,
            signers = List(guest),
            range = late,
            outputs = List(payTo(host, 0))
          )
        )
    }

    // spec [CA-7]
    test("[CA-7] feeBps above the maximum is capped at 5000 bps") {
        val late = upTo(checkIn - notice)
        // 100 ADA at 9000 bps would be 90 ADA; capped at 50% it is 50 ADA.
        assert(
          !single(
            booking(9000),
            Action.Cancel.toData,
            deposit = 100_000_000,
            signers = List(guest),
            range = late,
            outputs = List(payTo(host, 90_000_000))
          )
        )
        assert(
          single(
            booking(9000),
            Action.Cancel.toData,
            deposit = 100_000_000,
            signers = List(guest),
            range = late,
            outputs = List(payTo(host, 50_000_000))
          )
        )
    }

    // spec [CA-11]
    test("[CA-11] a deposit below the minimum fee is never over-charged") {
        val late = upTo(checkIn - notice)
        // deposit 1.5 ADA, minimum fee 2 ADA: the fee is the whole deposit
        assert(
          single(
            booking(0),
            Action.Cancel.toData,
            deposit = 1_500_000,
            signers = List(guest),
            range = late,
            outputs = List(payTo(host, 1_500_000))
          )
        )
    }

    // spec [CA-13]
    test("[CA-13] late cancel fails if the host receives less than the fee") {
        assert(
          !single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(checkIn - notice),
            outputs = List(payTo(host, 2_000_000))
          )
        )
    }

    // spec [CA-13]
    test("[CA-13] late cancel fails if the host gets one lovelace more than the fee") {
        assert(
          !single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(checkIn - notice),
            outputs = List(payTo(host, 2_500_002))
          )
        )
    }

    // spec [CA-13]: the host output sum adds up over several outputs, as in SPEC.md's example
    test("[CA-13] late cancel accepts the fee split over two host outputs") {
        assert(
          single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(checkIn - notice),
            outputs = List(payTo(host, 2_000_000), payTo(host, 500_001))
          )
        )
    }

    // spec [CA-14]
    test("[CA-14] late cancel does not check the guest output") {
        assert(
          single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(checkIn - notice),
            outputs = List(payTo(host, 2_500_001), payTo(guest, 12_345))
          )
        )
    }

    // spec [CA-13]: the host output sum ignores the staking part of the address (SPEC.md, Definitions)
    test("[CA-13] a host output with a staking part counts toward the fee") {
        val staked = v2.TxOut(
          v1.Address(
            v1.Credential.PubKeyCredential(host),
            Option.Some(v1.StakingCredential.StakingHash(v1.Credential.PubKeyCredential(guest)))
          ),
          v1.Value.lovelace(2_500_001),
          v2.OutputDatum.NoOutputDatum,
          Option.None
        )
        assert(
          single(
            booking(),
            Action.Cancel.toData,
            deposit = 25_000_001,
            signers = List(guest),
            range = upTo(checkIn - notice),
            outputs = List(staked)
          )
        )
    }

    // spec [RE-2]: the guest output sum ignores the staking part of the address (SPEC.md, Definitions)
    test("[RE-2] a guest output with a staking part counts toward the deposit") {
        val staked = v2.TxOut(
          v1.Address(
            v1.Credential.PubKeyCredential(guest),
            Option.Some(v1.StakingCredential.StakingHash(v1.Credential.PubKeyCredential(host)))
          ),
          v1.Value.lovelace(100_000_000),
          v2.OutputDatum.NoOutputDatum,
          Option.None
        )
        assert(single(booking(), Action.Release.toData, signers = List(host), outputs = List(staked)))
    }

    // ----- Release ----------------------------------------------------------------------------

    // spec [RE-1]
    test("[RE-1] release fails unless the host signs") {
        assert(
          !single(
            booking(),
            Action.Release.toData,
            signers = List(guest),
            outputs = List(payTo(guest, 100_000_000))
          )
        )
    }

    // spec [RE-2]
    test("[RE-2] release fails when the guest receives less than the deposit") {
        assert(
          !single(
            booking(),
            Action.Release.toData,
            signers = List(host),
            outputs = List(payTo(guest, 99_999_999))
          )
        )
    }

    // spec [RE-2] [RE-4]
    test("[RE-2] release at exactly the deposit passes") {
        assert(
          single(
            booking(),
            Action.Release.toData,
            signers = List(host),
            outputs = List(payTo(guest, 100_000_000))
          )
        )
    }

    // spec [RE-4]
    test("[RE-4] release may pay the guest more than the deposit") {
        assert(
          single(
            booking(),
            Action.Release.toData,
            signers = List(host),
            outputs = List(payTo(guest, 120_000_000))
          )
        )
    }

    // spec [RE-3]
    test("[RE-3] release does not check the validity range") {
        assert(
          single(
            booking(),
            Action.Release.toData,
            signers = List(host),
            range = v1.Interval.never,
            outputs = List(payTo(guest, 100_000_000))
          )
        )
    }

    // spec [RE-2] [SP-5]: the host's own payment to the guest counts only to the guest's key hash
    test("[RE-2] release does not count outputs paid to the host") {
        assert(
          !single(
            booking(),
            Action.Release.toData,
            signers = List(host),
            outputs = List(payTo(host, 100_000_000))
          )
        )
    }

    // ----- Claim ------------------------------------------------------------------------------

    // spec [CL-1]
    test("[CL-1] claim fails unless the host signs") {
        assert(
          !single(booking(), Action.Claim.toData, signers = List(guest), range = from(checkIn + grace + 1))
        )
    }

    // spec [CL-2]
    test("[CL-2] claim fails when L is infinite") {
        assert(
          !single(
            booking(),
            Action.Claim.toData,
            signers = List(host),
            range = v1.Interval.always
          )
        )
    }

    // spec [CL-3]
    test("[CL-3] claim at exactly checkIn + grace period fails") {
        assert(
          !single(
            booking(),
            Action.Claim.toData,
            signers = List(host),
            range = from(checkIn + grace)
          )
        )
    }

    // spec [CL-3]
    test("[CL-3] claim one millisecond after checkIn + grace period passes") {
        assert(
          single(
            booking(),
            Action.Claim.toData,
            signers = List(host),
            range = from(checkIn + grace + 1)
          )
        )
    }

    // spec [CL-4]
    test("[CL-4] claim ignores U") {
        assert(
          single(
            booking(),
            Action.Claim.toData,
            signers = List(host),
            range = v1.Interval(
              v1.IntervalBound.finiteInclusive(checkIn + grace + 1),
              v1.IntervalBound.posInf
            )
          )
        )
    }

    // spec [CL-5]
    test("[CL-5] claim checks no output") {
        assert(
          single(
            booking(),
            Action.Claim.toData,
            signers = List(host),
            range = from(checkIn + grace + 1),
            outputs = Nil
          )
        )
    }
}
