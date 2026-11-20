package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.{ByteString, Data}
import scalus.uplc.builtin.Data.toData
import scalus.uplc.eval.Result
import scalus.testing.kit.ScalusTest
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.prelude.{List as PList, Option as POption}

/** Unit tests of [[Deposit]]. Each test builds the script context by hand and
  * runs the compiled validator. Every test names the rule of SPEC.md that it
  * checks.
  */
class DepositTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default
    private val contract = PlutusV3.compile(Deposit.validate)

    // Parties.
    private val guest = PubKeyHash(ByteString.fromHex("11" * 28))
    private val host = PubKeyHash(ByteString.fromHex("22" * 28))
    private val stranger = PubKeyHash(ByteString.fromHex("33" * 28))

    // Times, in POSIX milliseconds. The late limit is checkIn - 1 hour, and the claim limit
    // is checkIn + 6 hours. Both are the values of the spec, Cancel and Claim examples.
    private val checkIn: BigInt = 1_100_000_000
    private val cancelBy: BigInt = 1_000_000_000
    private val lateLimit: BigInt = 1_096_400_000
    private val claimLimit: BigInt = 1_121_600_000

    private val depositAmount: BigInt = 100_000_000
    private val booking = Booking(guest, host, cancelBy, checkIn, BigInt(1000))

    private val ownRef =
        TxOutRef(TxId(ByteString.fromHex("aa" * 32)), BigInt(0))
    private val otherRef =
        TxOutRef(TxId(ByteString.fromHex("bb" * 32)), BigInt(1))
    private val deposit = ByteString.fromHex("cc" * 28)

    private val cancel = Action.Cancel.toData
    private val release = Action.Release.toData
    private val claim = Action.Claim.toData

    private def depositInput(
        ref: TxOutRef,
        lovelace: BigInt,
        b: Booking
    ): TxInInfo =
        TxInInfo(
          ref,
          TxOut(
            Address.fromScriptHash(deposit),
            Value.lovelace(lovelace),
            OutputDatum.OutputDatum(b.toData)
          )
        )

    private def payKey(pkh: PubKeyHash, lovelace: BigInt): TxOut =
        TxOut(Address.fromPubKeyHash(pkh), Value.lovelace(lovelace))

    private def plist[A](xs: Seq[A]): PList[A] =
        xs.foldRight(PList.Nil: PList[A])((x, acc) => PList.Cons(x, acc))

    /** Validity range with a finite upper bound (exclusive) and no lower bound.
      */
    private def upperAt(u: BigInt): Interval =
        Interval(IntervalBound.negInf, IntervalBound.finiteExclusive(u))

    /** Validity range with a finite lower bound (inclusive) and no upper bound.
      */
    private def lowerAt(l: BigInt): Interval =
        Interval(IntervalBound.finiteInclusive(l), IntervalBound.posInf)

    /** Runs the compiled validator on a spend of the own deposit, with the
      * given parts.
      */
    private def run(
        redeemer: Data,
        b: Booking = booking,
        lovelace: BigInt = depositAmount,
        spendRef: TxOutRef = ownRef,
        extraInputs: Seq[TxInInfo] = Nil,
        outputs: Seq[TxOut] = Nil,
        signers: Seq[PubKeyHash] = Nil,
        range: Interval = Interval.always,
        noDatum: Boolean = false,
        rawDatum: Option[Data] = None
    ): Result = {
        val datum: POption[Data] =
            if noDatum then POption.None
            else POption.Some(rawDatum.getOrElse(b.toData))
        contract.program.runWithDebug(
          ScriptContext(
            txInfo = TxInfo.placeholder.copy(
              inputs = plist(depositInput(ownRef, lovelace, b) +: extraInputs),
              outputs = plist(outputs),
              signatories = plist(signers),
              validRange = range
            ),
            redeemer = redeemer,
            scriptInfo = ScriptInfo.SpendingScript(spendRef, datum)
          )
        )
    }

    // ---- Common checks ----

    // spec [SP-1]
    test("the datum is required") {
        assert(
          run(
            release,
            signers = Seq(host),
            outputs = Seq(payKey(guest, depositAmount)),
            noDatum = true
          ).isFailure
        )
    }

    // spec [SP-2]
    test("the datum must decode as Booking") {
        assert(run(release, rawDatum = Some(BigInt(7).toData)).isFailure)
    }

    // spec [SP-3]
    test("the redeemer must decode as Action") {
        assert(run(BigInt(7).toData).isFailure)
    }

    // spec [SP-4]
    test("ownRef must name an input of the transaction") {
        assert(
          run(
            release,
            spendRef = otherRef,
            signers = Seq(host),
            outputs = Seq(payKey(guest, depositAmount))
          ).isFailure
        )
    }

    // spec [SP-5] [RE-2]
    test(
      "two deposit inputs fail, even when the guest receives both deposits"
    ) {
        assert(
          run(
            release,
            extraInputs = Seq(depositInput(otherRef, depositAmount, booking)),
            signers = Seq(host),
            outputs = Seq(payKey(guest, depositAmount * 2))
          ).isFailure
        )
    }

    // spec [SP-6] [CA-4]
    test("the one-deposit rule applies to Cancel") {
        val extra = Seq(depositInput(otherRef, depositAmount, booking))
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(cancelBy),
            extraInputs = extra
          ).isFailure
        )
    }

    // spec [SP-6] [CL-3]
    test("the one-deposit rule applies to Claim") {
        val extra = Seq(depositInput(otherRef, depositAmount, booking))
        assert(
          run(
            claim,
            signers = Seq(host),
            range = lowerAt(claimLimit + 1),
            extraInputs = extra
          ).isFailure
        )
    }

    // spec [SP-7] [CA-13]
    test("the deposit is the lovelace of the own input") {
        val lovelace = BigInt(1_500_000)
        // Fee is min(1.5 ADA, deposit) = 1.5 ADA, so paying 1.5 ADA passes and 2 ADA fails.
        assert(
          run(
            cancel,
            lovelace = lovelace,
            signers = Seq(guest),
            range = upperAt(lateLimit),
            outputs = Seq(payKey(host, lovelace))
          ).isSuccess
        )
        assert(
          run(
            cancel,
            lovelace = lovelace,
            signers = Seq(guest),
            range = upperAt(lateLimit),
            outputs = Seq(payKey(host, BigInt(2_000_000)))
          ).isFailure
        )
    }

    // spec [SP-8] [RE-2]
    test("every check must hold: host signs but guest is underpaid") {
        assert(
          run(
            release,
            signers = Seq(host),
            outputs = Seq(payKey(guest, depositAmount - 1))
          ).isFailure
        )
    }

    // spec [SP-9]
    test("host equal to guest is not a special case") {
        val same = booking.copy(host = guest)
        assert(
          run(
            release,
            b = same,
            signers = Seq(guest),
            outputs = Seq(payKey(guest, depositAmount))
          ).isSuccess
        )
    }

    // spec [SP-9] [CA-5]
    test("a cancelBy after the late limit still allows a free cancel") {
        val late = booking.copy(cancelBy = checkIn - 1_000)
        assert(
          run(
            cancel,
            b = late,
            signers = Seq(guest),
            range = upperAt(late.cancelBy)
          ).isSuccess
        )
    }

    // ---- Cancel ----

    // spec [CA-1]
    test("Cancel must be signed by the guest") {
        assert(
          run(cancel, signers = Seq(host), range = upperAt(cancelBy)).isFailure
        )
        assert(run(cancel, range = upperAt(cancelBy)).isFailure)
    }

    // spec [CA-2]
    test("Cancel fails when the upper bound is infinite") {
        assert(
          run(cancel, signers = Seq(guest), range = Interval.always).isFailure
        )
        assert(
          run(cancel, signers = Seq(guest), range = lowerAt(cancelBy)).isFailure
        )
    }

    // spec [CA-3]
    test("Cancel ignores the lower bound") {
        val range = Interval(
          IntervalBound.finiteInclusive(checkIn),
          IntervalBound.finiteExclusive(cancelBy)
        )
        assert(run(cancel, signers = Seq(guest), range = range).isSuccess)
    }

    // spec [CA-4]
    test("a free cancel with U equal to cancelBy needs no output") {
        assert(
          run(cancel, signers = Seq(guest), range = upperAt(cancelBy)).isSuccess
        )
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(cancelBy + 1)
          ).isFailure
        )
    }

    // spec [CA-5]
    test("the free test runs before the late test") {
        // cancelBy is later than the late limit, so a late test first would wrongly demand a fee.
        val late = booking.copy(cancelBy = checkIn - 1_000)
        assert(
          run(
            cancel,
            b = late,
            signers = Seq(guest),
            range = upperAt(late.cancelBy)
          ).isSuccess
        )
    }

    // spec [CA-6]
    test(
      "a late cancel with U after the late limit fails, even with the exact fee"
    ) {
        val fee = Seq(payKey(host, BigInt(10_000_000)))
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(lateLimit + 1),
            outputs = fee
          ).isFailure
        )
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(lateLimit),
            outputs = fee
          ).isSuccess
        )
    }

    // spec [CA-7]
    test("the rate is capped at 5000 bps") {
        val capped = booking.copy(feeBps = BigInt(9000))
        def late(paid: BigInt) = run(
          cancel,
          b = capped,
          signers = Seq(guest),
          range = upperAt(lateLimit),
          outputs = Seq(payKey(host, paid))
        )
        assert(late(BigInt(50_000_000)).isSuccess)
        assert(late(BigInt(90_000_000)).isFailure)
    }

    // spec [CA-8]
    test("a negative feeBps is treated as 0, so the minimum fee applies") {
        val negative = booking.copy(feeBps = BigInt(-5))
        def late(paid: BigInt) = run(
          cancel,
          b = negative,
          signers = Seq(guest),
          range = upperAt(lateLimit),
          outputs = Seq(payKey(host, paid))
        )
        assert(late(BigInt(2_000_000)).isSuccess)
        assert(late(BigInt(0)).isFailure)
    }

    // spec [CA-9]
    test("the raw fee rounds up") {
        val odd = depositAmount / 4 + 1 // 25_000_001
        def late(paid: BigInt) = run(
          cancel,
          lovelace = odd,
          signers = Seq(guest),
          range = upperAt(lateLimit),
          outputs = Seq(payKey(host, paid))
        )
        assert(late(BigInt(2_500_001)).isSuccess)
        assert(late(BigInt(2_500_000)).isFailure)
    }

    // spec [CA-10]
    test("the fee is raised to the minimum fee") {
        def late(lovelace: BigInt, paid: BigInt) = run(
          cancel,
          b = booking.copy(feeBps = BigInt(1000)),
          lovelace = lovelace,
          signers = Seq(guest),
          range = upperAt(lateLimit),
          outputs = Seq(payKey(host, paid))
        )
        // 10 ADA at 10 % is 1 ADA, raised to 2 ADA.
        assert(late(BigInt(10_000_000), BigInt(2_000_000)).isSuccess)
        assert(late(BigInt(10_000_000), BigInt(1_000_000)).isFailure)
    }

    // spec [CA-11]
    test("the fee is lowered to the deposit") {
        def late(paid: BigInt) = run(
          cancel,
          lovelace = BigInt(1_500_000),
          signers = Seq(guest),
          range = upperAt(lateLimit),
          outputs = Seq(payKey(host, paid))
        )
        assert(late(BigInt(1_500_000)).isSuccess)
        assert(late(BigInt(2_000_000)).isFailure)
    }

    // spec [CA-12] [CA-7] [CA-8] [CA-9] [CA-10] [CA-11]
    test("the fee test vectors of the spec") {
        val vectors = Seq(
          (BigInt(25_000_001), BigInt(1000), BigInt(2_500_001)),
          (BigInt(100_000_000), BigInt(9000), BigInt(50_000_000)),
          (BigInt(100_000_000), BigInt(0), BigInt(2_000_000)),
          (BigInt(10_000_000), BigInt(1000), BigInt(2_000_000)),
          (BigInt(1_500_000), BigInt(1000), BigInt(1_500_000)),
          (BigInt(1_500_000), BigInt(0), BigInt(1_500_000))
        )
        for (lovelace, bps, fee) <- vectors do
            val b = booking.copy(feeBps = bps)
            def late(paid: BigInt) = run(
              cancel,
              b = b,
              lovelace = lovelace,
              signers = Seq(guest),
              range = upperAt(lateLimit),
              outputs = Seq(payKey(host, paid))
            )
            assert(
              late(fee).isSuccess,
              s"fee $fee for deposit $lovelace at $bps bps"
            )
            assert(late(fee - 1).isFailure, s"fee ${fee - 1} must fail")
            assert(late(fee + 1).isFailure, s"fee ${fee + 1} must fail")
    }

    // spec [CA-13]
    test("the host output sum must equal the fee exactly") {
        // Deposit 25_000_001 at 10 %: fee 2_500_001 (spec example).
        val lovelace = BigInt(25_000_001)
        def late(outputs: Seq[TxOut]) = run(
          cancel,
          lovelace = lovelace,
          signers = Seq(guest),
          range = upperAt(lateLimit),
          outputs = outputs
        )
        assert(late(Seq(payKey(host, BigInt(2_500_002)))).isFailure)
        assert(late(Seq(payKey(host, BigInt(2_500_000)))).isFailure)
        // The fee of 2_500_001 may be paid in two outputs to the host.
        val split = Seq(
          payKey(host, BigInt(2_000_000)),
          payKey(host, BigInt(500_001))
        )
        assert(late(split).isSuccess)
    }

    // spec [CA-13] (host output sum ignores the staking part of the address)
    test("host outputs with a staking part count toward the fee") {
        // Default booking: deposit 100 ADA at 10 %, fee 10 ADA.
        val withStake = TxOut(
          Address(
            Credential.PubKeyCredential(host),
            POption.Some(
              StakingCredential.StakingHash(
                Credential.PubKeyCredential(stranger)
              )
            )
          ),
          Value.lovelace(10_000_000)
        )
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(lateLimit),
            outputs = Seq(withStake)
          ).isSuccess
        )
    }

    // spec [CA-13] (only host outputs count)
    test("paying the fee to the guest does not pay the host") {
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(lateLimit),
            outputs = Seq(payKey(guest, BigInt(10_000_000)))
          ).isFailure
        )
    }

    // spec [CA-14]
    test("a late cancel does not check the guest output") {
        val exactHostFee = Seq(payKey(host, BigInt(10_000_000)))
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(lateLimit),
            outputs = exactHostFee
          ).isSuccess
        )
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(lateLimit),
            outputs = exactHostFee :+ payKey(guest, BigInt(1))
          ).isSuccess
        )
    }

    // spec [CA-15]
    test("a free cancel does not check any output") {
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(cancelBy),
            outputs = Nil
          ).isSuccess
        )
        assert(
          run(
            cancel,
            signers = Seq(guest),
            range = upperAt(cancelBy),
            outputs = Seq(payKey(host, BigInt(9_000_000)))
          ).isSuccess
        )
    }

    // ---- Release ----

    // spec [RE-1]
    test("Release must be signed by the host") {
        val paid = Seq(payKey(guest, depositAmount))
        assert(run(release, signers = Seq(guest), outputs = paid).isFailure)
        assert(run(release, outputs = paid).isFailure)
    }

    // spec [RE-2]
    test("Release fails when the guest output sum is below the deposit") {
        assert(
          run(
            release,
            signers = Seq(host),
            outputs = Seq(payKey(guest, depositAmount - 1))
          ).isFailure
        )
        assert(
          run(
            release,
            signers = Seq(host),
            outputs = Seq(payKey(guest, depositAmount))
          ).isSuccess
        )
    }

    // spec [RE-3]
    test("Release does not check the validity range") {
        val paid = Seq(payKey(guest, depositAmount))
        assert(
          run(
            release,
            signers = Seq(host),
            outputs = paid,
            range = Interval.always
          ).isSuccess
        )
        assert(
          run(
            release,
            signers = Seq(host),
            outputs = paid,
            range = upperAt(cancelBy)
          ).isSuccess
        )
    }

    // spec [RE-4]
    test("Release may pay the guest more than the deposit") {
        assert(
          run(
            release,
            signers = Seq(host),
            outputs = Seq(payKey(guest, BigInt(120_000_000)))
          ).isSuccess
        )
    }

    // ---- Claim ----

    // spec [CL-1]
    test("Claim must be signed by the host") {
        assert(
          run(
            claim,
            signers = Seq(guest),
            range = lowerAt(claimLimit + 1)
          ).isFailure
        )
    }

    // spec [CL-2]
    test("Claim fails when the lower bound is infinite") {
        assert(
          run(claim, signers = Seq(host), range = Interval.always).isFailure
        )
        assert(
          run(
            claim,
            signers = Seq(host),
            range = upperAt(claimLimit + 1_000)
          ).isFailure
        )
    }

    // spec [CL-3]
    test("Claim needs L strictly after checkIn plus the grace period") {
        assert(
          run(claim, signers = Seq(host), range = lowerAt(claimLimit)).isFailure
        )
        assert(
          run(
            claim,
            signers = Seq(host),
            range = lowerAt(claimLimit + 1)
          ).isSuccess
        )
    }

    // spec [CL-4]
    test("Claim ignores the upper bound") {
        val range = Interval(
          IntervalBound.finiteInclusive(claimLimit + 1),
          IntervalBound.finiteExclusive(checkIn)
        )
        assert(run(claim, signers = Seq(host), range = range).isSuccess)
    }

    // spec [CL-5]
    test("Claim checks no output") {
        assert(
          run(
            claim,
            signers = Seq(host),
            range = lowerAt(claimLimit + 1)
          ).isSuccess
        )
        assert(
          run(
            claim,
            signers = Seq(host),
            range = lowerAt(claimLimit + 1),
            outputs = Seq(payKey(stranger, BigInt(1)))
          ).isSuccess
        )
    }
}
