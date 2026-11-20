package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.ByteString
import scalus.uplc.builtin.Data
import scalus.uplc.builtin.Data.toData
import scalus.cardano.onchain.plutus.v1.{
    Address,
    Credential,
    Interval,
    IntervalBound,
    PubKeyHash,
    StakingCredential,
    Value
}
import scalus.cardano.onchain.plutus.v2.{OutputDatum, TxOut}
import scalus.cardano.onchain.plutus.v3.{
    ScriptContext,
    ScriptInfo,
    TxId,
    TxInInfo,
    TxInfo,
    TxOutRef
}
import scalus.cardano.onchain.plutus.prelude.{List => PList, Option => POption}
import scalus.testing.kit.ScalusTest

/** Unit tests of `Deposit.spend`. Each test runs the compiled validator on a hand-built
  * ScriptContext, so validity bounds can be set to the exact millisecond.
  */
class DepositTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default
    private val contract = PlutusV3.compile(Deposit.validate)

    // ---- keys and constants -------------------------------------------------

    private def key(c: Char): PubKeyHash = PubKeyHash(ByteString.fromHex(c.toString * 56))

    private val guest = key('a')
    private val host = key('b')
    private val stranger = key('c')

    private val DEPOSIT: BigInt = 100_000_000
    private val FEE_10_PERCENT: BigInt = 10_000_000 // 100_000_000 * 1000 / 10000

    private val CANCEL_BY: BigInt = 1_000_000_000_000L
    private val CHECK_IN: BigInt = 1_000_086_400_000L
    private val LATE_START: BigInt = 1_000_082_800_000L // CHECK_IN - LATE_CUTOFF
    private val CLAIM_AFTER: BigInt = 1_000_021_600_000L // checkIn + GRACE, with checkIn = 1e12

    private val defaultBooking = Booking(guest, host, CANCEL_BY, CHECK_IN, BigInt(1000))
    private val claimBooking = defaultBooking.copy(checkIn = 1_000_000_000_000L)

    // ---- transaction builders ----------------------------------------------

    private val scriptHash = ByteString.fromHex("cc" * 28)
    private val scriptCred = Credential.ScriptCredential(scriptHash)
    private val ownRef = TxOutRef(TxId(ByteString.fromHex("ff" * 32)), BigInt(0))
    private val otherRef = TxOutRef(TxId(ByteString.fromHex("fe" * 32)), BigInt(1))

    private def scriptAddress: Address = Address(scriptCred, POption.None)
    private def keyAddress(pkh: PubKeyHash): Address =
        Address(Credential.PubKeyCredential(pkh), POption.None)

    private def out(addr: Address, value: Value): TxOut =
        TxOut(addr, value, OutputDatum.NoOutputDatum, POption.None)

    private def payKey(pkh: PubKeyHash, lovelace: BigInt): TxOut =
        out(keyAddress(pkh), Value.lovelace(lovelace))

    private def ownInput(value: Value = Value.lovelace(DEPOSIT)): TxInInfo =
        TxInInfo(ownRef, out(scriptAddress, value))

    private def excl(t: BigInt): IntervalBound = IntervalBound.finiteExclusive(t)
    private def incl(t: BigInt): IntervalBound = IntervalBound.finiteInclusive(t)

    private def txWith(
        inputs: Seq[TxInInfo] = Seq(ownInput()),
        refInputs: Seq[TxInInfo] = Seq.empty,
        outputs: Seq[TxOut] = Seq.empty,
        signers: Seq[PubKeyHash] = Seq(guest, host),
        range: Interval = Interval(IntervalBound.negInf, IntervalBound.posInf)
    ): TxInfo =
        TxInfo.placeholder.copy(
          inputs = PList.from(inputs),
          referenceInputs = PList.from(refInputs),
          outputs = PList.from(outputs),
          signatories = PList.from(signers),
          validRange = range
        )

    /** Runs the validator on a spend of `ownRef`. */
    private def run(
        tx: TxInfo,
        redeemer: Data,
        datum: POption[Data],
        ref: TxOutRef = ownRef
    ): Boolean =
        contract.program
            .runWithDebug(ScriptContext(tx, redeemer, ScriptInfo.SpendingScript(ref, datum)))
            .isSuccess

    /** Runs `action` for `booking` on a default transaction, with the given overrides. */
    private def check(
        action: Action,
        booking: Booking = defaultBooking,
        inputs: Seq[TxInInfo] = Seq(ownInput()),
        refInputs: Seq[TxInInfo] = Seq.empty,
        outputs: Seq[TxOut] = Seq.empty,
        signers: Seq[PubKeyHash] = Seq(guest, host),
        range: Interval = Interval(IntervalBound.negInf, IntervalBound.posInf)
    ): Boolean =
        run(
          txWith(inputs, refInputs, outputs, signers, range),
          action.toData,
          POption.Some(booking.toData)
        )

    /** A late cancel of the default booking, with `paid` lovelace to the host. */
    private def lateCancel(
        paid: BigInt,
        booking: Booking = defaultBooking,
        inputs: Seq[TxInInfo] = Seq(ownInput()),
        upper: BigInt = CANCEL_BY + 1
    ): Boolean =
        check(
          Action.Cancel,
          booking = booking,
          inputs = inputs,
          outputs = Seq(payKey(host, paid)),
          signers = Seq(guest),
          range = Interval(IntervalBound.negInf, excl(upper))
        )

    // ---- [GEN] general checks ------------------------------------------------

    // spec [GEN-1]
    test("GEN-1: fails when the datum is None") {
        assert(!run(txWith(), Action.Cancel.toData, POption.None))
    }

    // spec [GEN-2]
    test("GEN-2: fails when the datum does not decode as Booking") {
        assert(!run(txWith(), Action.Cancel.toData, POption.Some(BigInt(1).toData)))
    }

    // spec [GEN-3]
    test("GEN-3: fails when the redeemer does not decode as Action") {
        assert(!run(txWith(), BigInt(7).toData, POption.Some(defaultBooking.toData)))
    }

    // spec [GEN-4]
    test("GEN-4: fails when ownRef matches no input") {
        assert(
          !run(
            txWith(signers = Seq(guest, host)),
            Action.Release.toData,
            POption.Some(defaultBooking.toData),
            ref = otherRef
          )
        )
    }

    // spec [GEN-5]
    test("GEN-5: fails when the own input is not at a script address") {
        val pubKeyInput = TxInInfo(ownRef, out(keyAddress(guest), Value.lovelace(DEPOSIT)))
        assert(
          !check(
            Action.Release,
            inputs = Seq(pubKeyInput),
            outputs = Seq(payKey(guest, DEPOSIT)),
            signers = Seq(host)
          )
        )
    }

    // spec [GEN-6]
    test("GEN-6: fails when a second input has the same script credential") {
        val second = TxInInfo(otherRef, out(scriptAddress, Value.lovelace(5_000_000)))
        assert(
          !check(
            Action.Release,
            inputs = Seq(ownInput(), second),
            outputs = Seq(payKey(guest, DEPOSIT + 5_000_000)),
            signers = Seq(host)
          )
        )
    }

    // spec [GEN-6]
    test("GEN-6: accepts a second input from another script") {
        val otherScript = TxInInfo(
          otherRef,
          out(Address(Credential.ScriptCredential(ByteString.fromHex("dd" * 28)), POption.None), Value.lovelace(5_000_000))
        )
        assert(
          check(
            Action.Release,
            inputs = Seq(ownInput(), otherScript),
            outputs = Seq(payKey(guest, DEPOSIT)),
            signers = Seq(host)
          )
        )
    }

    // spec [GEN-6]
    test("GEN-6: reference inputs do not count as inputs") {
        val refFromSameScript = TxInInfo(otherRef, out(scriptAddress, Value.lovelace(5_000_000)))
        assert(
          check(
            Action.Release,
            refInputs = Seq(refFromSameScript),
            outputs = Seq(payKey(guest, DEPOSIT)),
            signers = Seq(host)
          )
        )
    }

    // spec [GEN-7]
    test("GEN-7: a booking with host equal to guest, cancelBy after checkIn and negative feeBps decodes and is not checked") {
        val odd = Booking(guest, guest, CHECK_IN + 1, CHECK_IN, BigInt(-5000))
        assert(check(Action.Cancel, booking = odd, signers = Seq(guest), range = Interval(IntervalBound.negInf, excl(CHECK_IN + 1))))
    }

    // spec [GEN-7]
    test("GEN-7: a booking with feeBps above the maximum is not rejected at spend time") {
        val odd = defaultBooking.copy(feeBps = BigInt(60000))
        assert(check(Action.Release, booking = odd, outputs = Seq(payKey(guest, DEPOSIT)), signers = Seq(host)))
    }

    // spec [GEN-9]
    test("GEN-9: general checks pass but the action check fails, so the transaction fails") {
        // Single valid script input, correct payment, but the guest signs a Release, not the host.
        assert(!check(Action.Release, signers = Seq(guest), outputs = Seq(payKey(guest, DEPOSIT))))
    }

    // spec [GEN-8]
    test("GEN-8: other assets in the deposit are ignored") {
        val tokenPolicy = ByteString.fromHex("dd" * 28)
        val tokenName = ByteString.fromHex("746f6b656e")
        val withToken = Value.plus(Value.lovelace(DEPOSIT), Value(tokenPolicy, tokenName, BigInt(5)))
        assert(
          check(
            Action.Release,
            inputs = Seq(ownInput(withToken)),
            outputs = Seq(payKey(guest, DEPOSIT)),
            signers = Seq(host)
          )
        )
        assert(
          !check(
            Action.Release,
            inputs = Seq(ownInput(withToken)),
            outputs = Seq(payKey(guest, DEPOSIT - 1)),
            signers = Seq(host)
          )
        )
    }

    // ---- [CAN] Cancel -------------------------------------------------------

    // spec [CAN-1]
    test("CAN-1: fails without the guest signature") {
        assert(!check(Action.Cancel, signers = Seq(host), range = Interval(IntervalBound.negInf, excl(CANCEL_BY))))
    }

    // spec [CAN-1]
    test("CAN-1: accepts with the guest signature") {
        assert(check(Action.Cancel, signers = Seq(guest), range = Interval(IntervalBound.negInf, excl(CANCEL_BY))))
    }

    // spec [CAN-10]
    test("CAN-10: the host alone cannot cancel for the guest, even when the cancel is free") {
        assert(!check(Action.Cancel, signers = Seq(host), range = Interval(IntervalBound.negInf, excl(CANCEL_BY))))
    }

    // spec [CAN-2]
    test("CAN-2: fails when U is absent") {
        assert(!check(Action.Cancel, signers = Seq(guest), range = Interval(IntervalBound.negInf, IntervalBound.posInf)))
    }

    // spec [CAN-3]
    test("CAN-3: free cancel at U = cancelBy with no output check") {
        assert(check(Action.Cancel, signers = Seq(guest), range = Interval(IntervalBound.negInf, excl(CANCEL_BY))))
    }

    // spec [CAN-8]
    test("CAN-8: the rest of the deposit may go anywhere on a free cancel") {
        assert(
          check(
            Action.Cancel,
            signers = Seq(guest),
            outputs = Seq(payKey(stranger, DEPOSIT)),
            range = Interval(IntervalBound.negInf, excl(CANCEL_BY))
          )
        )
    }

    // spec [CAN-3]
    test("CAN-3: a free cancel at U = cancelBy, given as an inclusive bound of cancelBy - 1") {
        assert(check(Action.Cancel, signers = Seq(guest), range = Interval(IntervalBound.negInf, incl(CANCEL_BY - 1))))
    }

    // spec [CAN-3]
    test("CAN-3: an inclusive bound of cancelBy gives U = cancelBy + 1, which is late") {
        assert(!check(Action.Cancel, signers = Seq(guest), range = Interval(IntervalBound.negInf, incl(CANCEL_BY))))
    }

    // spec [CAN-4]
    test("CAN-4: late cancel at U = cancelBy + 1 is accepted when the host gets the fee") {
        assert(lateCancel(FEE_10_PERCENT))
    }

    // spec [CAN-4]
    test("CAN-4: late cancel at U = checkIn - LATE_CUTOFF is accepted when the host gets the fee") {
        assert(lateCancel(FEE_10_PERCENT, upper = LATE_START))
    }

    // spec [CAN-5]
    test("CAN-5: fails at U = checkIn - LATE_CUTOFF + 1, even with the fee") {
        assert(!lateCancel(FEE_10_PERCENT, upper = LATE_START + 1))
    }

    // spec [CAN-6]
    test("CAN-6: late cancel fails when the host gets less than the fee") {
        assert(!lateCancel(FEE_10_PERCENT - 1))
    }

    // spec [CAN-6]
    test("CAN-6: late cancel fails when the host gets more than the fee") {
        assert(!lateCancel(FEE_10_PERCENT + 1))
    }

    // spec [CAN-6]
    test("CAN-6: late cancel fails when the host gets nothing") {
        assert(!lateCancel(BigInt(0)))
    }

    // spec [CAN-6]
    test("CAN-6: payment to the host counts by payment credential, so a staking part does not hide it") {
        val hostWithStake = Address(
          Credential.PubKeyCredential(host),
          POption.Some(StakingCredential.StakingHash(Credential.PubKeyCredential(stranger)))
        )
        val ok = check(
          Action.Cancel,
          signers = Seq(guest),
          outputs = Seq(out(hostWithStake, Value.lovelace(FEE_10_PERCENT))),
          range = Interval(IntervalBound.negInf, excl(CANCEL_BY + 1))
        )
        assert(ok)
    }

    // spec [CAN-6]
    test("CAN-6: a host output to a script credential does not count as paid to the host") {
        val toScript = out(Address(Credential.ScriptCredential(ByteString.fromHex("aa" * 28)), POption.None), Value.lovelace(FEE_10_PERCENT))
        assert(
          !check(
            Action.Cancel,
            signers = Seq(guest),
            outputs = Seq(toScript),
            range = Interval(IntervalBound.negInf, excl(CANCEL_BY + 1))
          )
        )
    }

    // spec [CAN-7]
    test("CAN-7: the lower bound is not checked for Cancel, a free cancel with a finite lower bound is accepted") {
        assert(
          check(
            Action.Cancel,
            signers = Seq(guest),
            range = Interval(incl(CHECK_IN * 2), excl(CANCEL_BY))
          )
        )
    }

    // spec [CAN-9]
    test("CAN-9: cancelBy later than checkIn - LATE_CUTOFF keeps the free window: U = cancelBy is free") {
        val booking = defaultBooking.copy(cancelBy = 1_000_090_000_000L)
        assert(
          check(
            Action.Cancel,
            booking = booking,
            signers = Seq(guest),
            range = Interval(IntervalBound.negInf, excl(1_000_090_000_000L))
          )
        )
    }

    // spec [CAN-9]
    test("CAN-9: cancelBy later than checkIn - LATE_CUTOFF: U = cancelBy + 1 fails") {
        val booking = defaultBooking.copy(cancelBy = 1_000_090_000_000L)
        assert(
          !check(
            Action.Cancel,
            booking = booking,
            signers = Seq(guest),
            range = Interval(IntervalBound.negInf, excl(1_000_090_000_001L))
          )
        )
    }

    // spec [CAN-4] [CAN-5] boundary table of section 4
    test("CAN-2..CAN-5: the section 4 table of U values") {
        assert(lateCancel(FEE_10_PERCENT, upper = CANCEL_BY + 1))
        assert(lateCancel(FEE_10_PERCENT, upper = LATE_START))
        assert(!lateCancel(FEE_10_PERCENT, upper = LATE_START + 1))
    }

    // ---- [FEE] fee computation, checked through a late cancel ------------

    // spec [FEE-2] [FEE-3] [FEE-4]: deposit 25_000_001, feeBps 1000 -> raw rounds up to 2_500_001
    test("FEE-2: the fee rounds up: 25_000_001 at 1000 bps is 2_500_001") {
        val booking = defaultBooking.copy(feeBps = BigInt(1000))
        val locked = Seq(ownInput(Value.lovelace(25_000_001)))
        assert(lateCancel(BigInt(2_500_001), booking = booking, inputs = locked))
        assert(!lateCancel(BigInt(2_500_000), booking = booking, inputs = locked))
    }

    // spec [FEE-3]: feeBps 0 gives MIN_FEE of 2 ADA
    test("FEE-3: feeBps 0 still costs MIN_FEE of 2_000_000") {
        val booking = defaultBooking.copy(feeBps = BigInt(0))
        assert(lateCancel(BigInt(2_000_000), booking = booking))
        assert(!lateCancel(BigInt(0), booking = booking))
    }

    // spec [FEE-1] [FEE-5]: feeBps 9000 is clamped to 5000, so 50 percent
    test("FEE-1, FEE-5: feeBps above MAX_BPS is clamped to 5000") {
        val booking = defaultBooking.copy(feeBps = BigInt(9000))
        assert(lateCancel(BigInt(50_000_000), booking = booking))
        assert(!lateCancel(BigInt(90_000_000), booking = booking))
    }

    // spec [FEE-4]: deposit 1_500_000 is below MIN_FEE, the fee never exceeds the deposit
    test("FEE-4: the fee is capped at the deposit") {
        val booking = defaultBooking.copy(feeBps = BigInt(1000))
        val locked = Seq(ownInput(Value.lovelace(1_500_000)))
        assert(lateCancel(BigInt(1_500_000), booking = booking, inputs = locked))
        assert(!lateCancel(BigInt(2_000_000), booking = booking, inputs = locked))
    }

    // spec [FEE-6]: negative feeBps does not fail; the fee is MIN_FEE
    test("FEE-6: a negative feeBps gives MIN_FEE and does not fail") {
        val booking = defaultBooking.copy(feeBps = BigInt(-5000))
        assert(lateCancel(BigInt(2_000_000), booking = booking))
        assert(!lateCancel(BigInt(0), booking = booking))
    }

    // ---- [REL] Release ------------------------------------------------------

    // spec [REL-1]
    test("REL-1: fails without the host signature") {
        assert(!check(Action.Release, signers = Seq(guest), outputs = Seq(payKey(guest, DEPOSIT))))
    }

    // spec [REL-2]
    test("REL-2: fails when the guest is paid one lovelace less than the deposit") {
        assert(!check(Action.Release, signers = Seq(host), outputs = Seq(payKey(guest, DEPOSIT - 1))))
    }

    // spec [REL-2]
    test("REL-2: accepts when the guest is paid exactly the deposit") {
        assert(check(Action.Release, signers = Seq(host), outputs = Seq(payKey(guest, DEPOSIT))))
    }

    // spec [REL-5]
    test("REL-5: accepts when the guest is paid more than the deposit") {
        assert(check(Action.Release, signers = Seq(host), outputs = Seq(payKey(guest, DEPOSIT + 20_000_000))))
    }

    // spec [REL-3]
    test("REL-3: no validity range is needed for Release") {
        assert(
          check(
            Action.Release,
            signers = Seq(host),
            outputs = Seq(payKey(guest, DEPOSIT)),
            range = Interval(incl(CHECK_IN + 1), IntervalBound.posInf)
          )
        )
    }

    // spec [REL-4]
    test("REL-4: the guest does not need to sign a Release") {
        assert(check(Action.Release, signers = Seq(host), outputs = Seq(payKey(guest, DEPOSIT))))
    }

    // spec [REL-2]
    test("REL-2: the guest must be paid by payment credential, a script output does not count") {
        val toScriptWithGuestBytes = out(
          Address(Credential.ScriptCredential(ByteString.fromHex("aa" * 28)), POption.None),
          Value.lovelace(DEPOSIT)
        )
        assert(!check(Action.Release, signers = Seq(host), outputs = Seq(toScriptWithGuestBytes)))
    }

    // ---- [CLM] Claim --------------------------------------------------------

    // spec [CLM-1]
    test("CLM-1: fails without the host signature") {
        assert(
          !check(
            Action.Claim,
            booking = claimBooking,
            signers = Seq(guest),
            range = Interval(incl(CLAIM_AFTER + 1), IntervalBound.posInf)
          )
        )
    }

    // spec [CLM-2]
    test("CLM-2: fails when L is absent") {
        assert(
          !check(
            Action.Claim,
            booking = claimBooking,
            signers = Seq(host),
            range = Interval(IntervalBound.negInf, IntervalBound.posInf)
          )
        )
    }

    // spec [CLM-3]
    test("CLM-3: fails at L = checkIn + GRACE, the bound is strict") {
        assert(
          !check(
            Action.Claim,
            booking = claimBooking,
            signers = Seq(host),
            range = Interval(incl(CLAIM_AFTER), IntervalBound.posInf)
          )
        )
    }

    // spec [CLM-3]
    test("CLM-3: accepts at L = checkIn + GRACE + 1") {
        assert(
          check(
            Action.Claim,
            booking = claimBooking,
            signers = Seq(host),
            range = Interval(incl(CLAIM_AFTER + 1), IntervalBound.posInf)
          )
        )
    }

    // spec [CLM-3] [2.1]: an exclusive lower bound of checkIn + GRACE gives L = checkIn + GRACE + 1
    test("CLM-3: an exclusive lower bound of checkIn + GRACE is accepted (L is one more)") {
        assert(
          check(
            Action.Claim,
            booking = claimBooking,
            signers = Seq(host),
            range = Interval(excl(CLAIM_AFTER), IntervalBound.posInf)
          )
        )
    }

    // spec [CLM-4]
    test("CLM-4: U is not checked for Claim") {
        assert(
          check(
            Action.Claim,
            booking = claimBooking,
            signers = Seq(host),
            range = Interval(incl(CLAIM_AFTER + 1), excl(CLAIM_AFTER + 2))
          )
        )
    }

    // spec [CLM-5]
    test("CLM-5: no output is checked for Claim") {
        assert(
          check(
            Action.Claim,
            booking = claimBooking,
            signers = Seq(host),
            outputs = Seq(payKey(stranger, 1)),
            range = Interval(incl(CLAIM_AFTER + 1), IntervalBound.posInf)
          )
        )
    }

    // ---- [2.1] L and U from the validity range -------------------------------

    // spec [2.1]: an inclusive upper bound of X gives U = X + 1, so an exclusive bound of X + 1 is the same
    test("2.1: inclusive upper bound X and exclusive upper bound X + 1 give the same U") {
        val inclusive = check(Action.Cancel, signers = Seq(guest), range = Interval(IntervalBound.negInf, incl(CANCEL_BY - 1)))
        val exclusive = check(Action.Cancel, signers = Seq(guest), range = Interval(IntervalBound.negInf, excl(CANCEL_BY)))
        assert(inclusive && exclusive)
    }
}
