package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.{ByteString, Data}
import scalus.uplc.builtin.Data.toData
import scalus.cardano.onchain.plutus.v1.{Interval, IntervalBound, IntervalBoundType}
import scalus.cardano.onchain.plutus.v2.OutputDatum
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.prelude.{List as PList, Option as POption, SortedMap}
import scalus.testing.kit.ScalusTest
import scalus.cardano.onchain.plutus.v3.ScriptContext
import scalus.uplc.eval.Result

class DepositTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default
    private val contract = PlutusV3.compile(Deposit.validate)

    private def pkh(b: Int) = PubKeyHash(ByteString.fromHex(f"$b%02x" * 28))
    private val guest = pkh(1)
    private val host = pkh(2)
    private val other = pkh(3)
    private val scriptHash = ByteString.fromHex("aa" * 28)
    private val scriptAddr = Address(Credential.ScriptCredential(scriptHash), POption.None)
    private def userAddr(k: PubKeyHash, staked: Boolean = false) =
        Address(Credential.PubKeyCredential(k), POption.None)

    private def txId(n: Int) = TxId(ByteString.fromHex(f"$n%02x" * 32))
    private val ownRef = TxOutRef(txId(1), BigInt(0))

    private val T = BigInt(1_000_000_000_000L)
    private val checkIn = T + 86_400_000L
    private def booking(
        cancelBy: BigInt = T,
        ci: BigInt = checkIn,
        bps: BigInt = BigInt(1000)
    ) = Booking(guest, host, cancelBy, ci, bps)

    private def lovelaceOut(a: Address, n: BigInt) =
        TxOut(a, Value.lovelace(n), OutputDatum.NoOutputDatum, POption.None)

    private def range(lo: IntervalBound, hi: IntervalBound) = Interval(lo, hi)
    private val negInf = IntervalBound.negInf
    private val posInf = IntervalBound.posInf
    private def upTo(u: BigInt) = range(negInf, IntervalBound.finiteExclusive(u))
    private def from(l: BigInt) = range(IntervalBound.finiteInclusive(l), posInf)

    private def eval(
        action: Data,
        b: Data = booking().toData,
        sigs: Seq[PubKeyHash] = Seq.empty,
        validRange: Interval = Interval.always,
        outs: Seq[TxOut] = Seq.empty,
        deposit: BigInt = BigInt(100_000_000),
        extraInputs: Seq[TxInInfo] = Seq.empty,
        datum: Option[Data] = None,
        ownAddr: Address = scriptAddr,
        ownInResolved: Option[TxOut] = None,
        refInputs: Seq[TxInInfo] = Seq.empty
    ): Result = {
        val own = TxInInfo(ownRef, ownInResolved.getOrElse(lovelaceOut(ownAddr, deposit)))
        val info = TxInfo.placeholder.copy(
          inputs = PList.from(own +: extraInputs),
          referenceInputs = PList.from(refInputs),
          outputs = PList.from(outs),
          validRange = validRange,
          signatories = PList.from(sigs)
        )
        val dat = datum.getOrElse(b)
        val ctx = ScriptContext(
          info,
          action,
          ScriptInfo.SpendingScript(ownRef, POption.Some(dat))
        )
        contract.program.runWithDebug(ctx)
    }
    private def evalNoDatum(): Result = {
        val info = TxInfo.placeholder.copy(inputs =
            PList.from(Seq(TxInInfo(ownRef, lovelaceOut(scriptAddr, BigInt(100_000_000)))))
        )
        contract.program.runWithDebug(
          ScriptContext(info, Action.Release.toData, ScriptInfo.SpendingScript(ownRef, POption.None))
        )
    }

    private val cancel = Action.Cancel.toData
    private val release = Action.Release.toData
    private val claim = Action.Claim.toData

    private def ok(r: Result) = assert(r.isSuccess, r.toString)
    private def bad(r: Result) = assert(r.isFailure, "expected failure")

    // ---- GEN ----
    test("[GEN-1] missing datum fails") { bad(evalNoDatum()) }
    test("[GEN-2] undecodable datum fails") {
        bad(eval(release, datum = Some(BigInt(5).toData), sigs = Seq(host), outs = Seq(lovelaceOut(userAddr(guest), 100_000_000))))
    }
    test("[GEN-3] undecodable redeemer fails") {
        bad(eval(BigInt(7).toData, sigs = Seq(host, guest)))
    }
    test("[GEN-4] own ref matching no input fails") {
        val info = TxInfo.placeholder.copy(inputs = PList.Nil)
        bad(
          contract.program.runWithDebug(
            ScriptContext(info, claim, ScriptInfo.SpendingScript(ownRef, POption.Some(booking().toData)))
          )
        )
    }
    test("[GEN-5] own input at pubkey address fails") {
        bad(eval(claim, sigs = Seq(host), validRange = from(checkIn + 21_600_001L), ownAddr = userAddr(host)))
    }
    test("[GEN-6] two inputs of the same script fail for every action") {
        val extra = TxInInfo(TxOutRef(txId(2), BigInt(0)), lovelaceOut(scriptAddr, BigInt(5_000_000)))
        bad(eval(claim, sigs = Seq(host), validRange = from(checkIn + 21_600_001L), extraInputs = Seq(extra)))
        bad(eval(cancel, sigs = Seq(guest), validRange = upTo(T), extraInputs = Seq(extra)))
        bad(eval(release, sigs = Seq(host), outs = Seq(lovelaceOut(userAddr(guest), 100_000_000)), extraInputs = Seq(extra)))
    }
    test("[GEN-6] script input of a different script hash does not count; reference inputs do not count") {
        val otherScript = Address(Credential.ScriptCredential(ByteString.fromHex("bb" * 28)), POption.None)
        val extra = TxInInfo(TxOutRef(txId(2), BigInt(0)), lovelaceOut(otherScript, BigInt(5_000_000)))
        val ref = TxInInfo(TxOutRef(txId(3), BigInt(0)), lovelaceOut(scriptAddr, BigInt(5_000_000)))
        ok(eval(claim, sigs = Seq(host), validRange = from(checkIn + 21_600_001L), extraInputs = Seq(extra), refInputs = Seq(ref)))
    }
    test("[GEN-7] no checks of booking fields (host == guest, cancelBy > checkIn, negative bps)") {
        val b = Booking(guest, guest, checkIn + 5, checkIn, BigInt(-5))
        ok(eval(cancel, b = b.toData, sigs = Seq(guest), validRange = upTo(checkIn + 5)))
    }
    test("[GEN-8] only lovelace counts: extra assets in outputs do not add to payment") {
        val tokenVal = Value.lovelace(99_999_999) + Value(ByteString.fromHex("cc" * 28), ByteString.fromString("t"), BigInt(1000))
        val out = TxOut(userAddr(guest), tokenVal, OutputDatum.NoOutputDatum, POption.None)
        bad(eval(release, sigs = Seq(host), outs = Seq(out)))
    }
    test("[GEN-8] only lovelace counts: deposit ignores assets of own input") {
        val ownVal = Value.lovelace(100_000_000) + Value(ByteString.fromHex("cc" * 28), ByteString.fromString("t"), BigInt(5))
        val own = TxOut(scriptAddr, ownVal, OutputDatum.NoOutputDatum, POption.None)
        ok(eval(release, sigs = Seq(host), ownInResolved = Some(own), outs = Seq(lovelaceOut(userAddr(guest), 100_000_000))))
    }
    test("[GEN-9] wrong-action signer does not pass") {
        bad(eval(claim, sigs = Seq(guest), validRange = from(checkIn + 21_600_001L)))
    }

    // ---- CANCEL ----
    private val cb = T
    private val late = checkIn - 3_600_000L
    test("[CAN-1] guest signature required") {
        bad(eval(cancel, sigs = Seq(other), validRange = upTo(T)))
        bad(eval(cancel, sigs = Seq.empty, validRange = upTo(T)))
    }
    test("[CAN-10] host signature alone does not cancel") {
        bad(eval(cancel, sigs = Seq(host), validRange = upTo(T)))
    }
    test("[CAN-2] U absent fails") {
        bad(eval(cancel, sigs = Seq(guest), validRange = Interval.always))
        bad(eval(cancel, sigs = Seq(guest), validRange = from(T - 100)))
    }
    test("[CAN-3] U == cancelBy is free, no output checked; [CAN-7] L ignored; [CAN-8]") {
        ok(eval(cancel, sigs = Seq(guest), validRange = upTo(T)))
        ok(eval(cancel, sigs = Seq(guest), validRange = range(IntervalBound.finiteExclusive(T + 99999999L), IntervalBound.finiteExclusive(T))))
    }
    test("[CAN-4] U = cancelBy+1 is late; fee applies") {
        val r = upTo(T + 1)
        ok(eval(cancel, sigs = Seq(guest), validRange = r, outs = Seq(lovelaceOut(userAddr(host), 10_000_000))))
        bad(eval(cancel, sigs = Seq(guest), validRange = r))
    }
    test("[CAN-4] U = checkIn - LATE_CUTOFF is late; [CAN-5] one more fails") {
        val outs = Seq(lovelaceOut(userAddr(host), 10_000_000))
        ok(eval(cancel, sigs = Seq(guest), validRange = upTo(late), outs = outs))
        bad(eval(cancel, sigs = Seq(guest), validRange = upTo(late + 1), outs = outs))
    }
    test("[CAN-2] [CAN-3] [CAN-4] [CAN-5] spec example table, plus inclusive upper bound") {
        // inclusive bound t gives U = t + 1
        ok(eval(cancel, sigs = Seq(guest), validRange = range(negInf, IntervalBound.finiteInclusive(T - 1))))
        bad(eval(cancel, sigs = Seq(guest), validRange = range(negInf, IntervalBound.finiteInclusive(late))))
        val outs = Seq(lovelaceOut(userAddr(host), 10_000_000))
        ok(eval(cancel, sigs = Seq(guest), validRange = range(negInf, IntervalBound.finiteInclusive(late - 1)), outs = outs))
    }
    test("[CAN-6] late cancel: host paid exactly the fee; under and over fail; sums across outputs and ignores staking part") {
        val r = upTo(T + 1)
        bad(eval(cancel, sigs = Seq(guest), validRange = r, outs = Seq(lovelaceOut(userAddr(host), 9_999_999))))
        bad(eval(cancel, sigs = Seq(guest), validRange = r, outs = Seq(lovelaceOut(userAddr(host), 10_000_001))))
        bad(eval(cancel, sigs = Seq(guest), validRange = r, outs = Seq(lovelaceOut(userAddr(host), 100_000_000))))
        ok(eval(cancel, sigs = Seq(guest), validRange = r, outs = Seq(lovelaceOut(userAddr(host), 4_000_000), lovelaceOut(userAddr(other), 1), lovelaceOut(userAddr(host), 6_000_000))))
        val staked = Address(Credential.PubKeyCredential(host), POption.Some(StakingCredential.StakingHash(Credential.PubKeyCredential(other))))
        ok(eval(cancel, sigs = Seq(guest), validRange = r, outs = Seq(lovelaceOut(staked, 10_000_000))))
    }
    test("[CAN-6] [CAN-8] guest part is free") {
        ok(eval(cancel, sigs = Seq(guest), validRange = upTo(T + 1), outs = Seq(lovelaceOut(userAddr(host), 10_000_000), lovelaceOut(userAddr(other), 1))))
    }
    test("[CAN-9] cancelBy after checkIn - cutoff keeps the free window") {
        val b = booking(cancelBy = BigInt(1_000_090_000_000L)).toData
        ok(eval(cancel, b = b, sigs = Seq(guest), validRange = upTo(BigInt(1_000_090_000_000L))))
        bad(eval(cancel, b = b, sigs = Seq(guest), validRange = upTo(BigInt(1_000_090_000_001L))))
    }

    // ---- FEE ----
    private def feeCase(deposit: BigInt, bps: BigInt, fee: BigInt) = {
        val b = booking(bps = bps).toData
        val r = upTo(T + 1)
        ok(eval(cancel, b = b, sigs = Seq(guest), validRange = r, deposit = deposit, outs = Seq(lovelaceOut(userAddr(host), fee))))
        bad(eval(cancel, b = b, sigs = Seq(guest), validRange = r, deposit = deposit, outs = Seq(lovelaceOut(userAddr(host), fee + 1))))
        bad(eval(cancel, b = b, sigs = Seq(guest), validRange = r, deposit = deposit, outs = Seq(lovelaceOut(userAddr(host), fee - 1))))
    }
    test("[FEE-2] rounds up") { feeCase(25_000_001, 1000, 2_500_001) }
    test("[FEE-3] MIN_FEE floor when bps 0") { feeCase(100_000_000, 0, 2_000_000) }
    test("[FEE-1] [FEE-5] bps clamped to MAX_BPS") { feeCase(100_000_000, 9000, 50_000_000) }
    test("[FEE-4] fee capped at deposit") { feeCase(1_500_000, 1000, 1_500_000) }
    test("[FEE-6] negative bps gives MIN_FEE") { feeCase(100_000_000, -500, 2_000_000) }

    // ---- RELEASE ----
    private val guestOut = (n: BigInt) => Seq(lovelaceOut(userAddr(guest), n))
    test("[REL-1] host signature required") {
        bad(eval(release, sigs = Seq(guest), outs = guestOut(100_000_000)))
        bad(eval(release, sigs = Seq.empty, outs = guestOut(100_000_000)))
    }
    test("[REL-2] [REL-5] guest paid at least the deposit") {
        ok(eval(release, sigs = Seq(host), outs = guestOut(100_000_000)))
        ok(eval(release, sigs = Seq(host), outs = guestOut(120_000_000)))
        bad(eval(release, sigs = Seq(host), outs = guestOut(99_999_999)))
        bad(eval(release, sigs = Seq(host), outs = Seq(lovelaceOut(scriptAddr, 100_000_000))))
    }
    test("[REL-3] [REL-4] any validity range, no guest signature") {
        ok(eval(release, sigs = Seq(host), outs = guestOut(100_000_000), validRange = Interval.never))
        ok(eval(release, sigs = Seq(host), outs = guestOut(100_000_000), validRange = upTo(T)))
    }

    // ---- CLAIM ----
    private val cl = checkIn + 21_600_000L
    test("[CLM-1] host signature required") {
        bad(eval(claim, sigs = Seq(guest), validRange = from(cl + 1)))
        bad(eval(claim, sigs = Seq.empty, validRange = from(cl + 1)))
    }
    test("[CLM-2] L absent fails") {
        bad(eval(claim, sigs = Seq(host), validRange = Interval.always))
        bad(eval(claim, sigs = Seq(host), validRange = upTo(cl + 100000)))
    }
    test("[CLM-3] strict after checkIn + GRACE; exclusive lower bound adds one") {
        bad(eval(claim, sigs = Seq(host), validRange = from(cl)))
        ok(eval(claim, sigs = Seq(host), validRange = from(cl + 1)))
        ok(eval(claim, sigs = Seq(host), validRange = range(IntervalBound.finiteExclusive(cl), posInf)))
        bad(eval(claim, sigs = Seq(host), validRange = range(IntervalBound.finiteExclusive(cl - 1), posInf)))
    }
    test("[CLM-4] [CLM-5] U and outputs not checked") {
        ok(eval(claim, sigs = Seq(host), validRange = range(IntervalBound.finiteInclusive(cl + 1), IntervalBound.finiteExclusive(cl + 2))))
        ok(eval(claim, sigs = Seq(host), validRange = from(cl + 1), outs = Seq.empty))
    }
}
