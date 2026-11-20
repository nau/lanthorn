package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.{ByteString, Data}
import scalus.uplc.builtin.ByteString.hex
import scalus.uplc.builtin.Data.toData
import scalus.cardano.onchain.plutus.prelude
import scalus.cardano.onchain.plutus.v3.*

class DepositTest extends AnyFunSuite, scalus.testing.kit.ScalusTest {

    private given Options = Options.default
    private val contract = PlutusV3.compile(Deposit.validate)

    private val guest = PubKeyHash(hex"11111111111111111111111111111111111111111111111111111111")
    private val host = PubKeyHash(hex"22222222222222222222222222222222222222222222222222222222")
    private val other = PubKeyHash(hex"33333333333333333333333333333333333333333333333333333333")
    private val scriptHash = hex"44444444444444444444444444444444444444444444444444444444"
    private val scriptAddr = Address(Credential.ScriptCredential(scriptHash), prelude.Option.None)
    private def pkAddr(h: PubKeyHash) = Address(Credential.PubKeyCredential(h), prelude.Option.None)
    private def tid(b: Int) = TxId(ByteString.fromHex(f"$b%02x" * 32))
    private val ownRef = TxOutRef(tid(1), BigInt(0))
    private val otherRef = TxOutRef(tid(2), BigInt(0))

    private val cancelBy = BigInt(1_000_000_000L)
    private val checkIn = BigInt(1_100_000_000L)
    private def booking(bps: BigInt = 1000) = Booking(guest, host, cancelBy, checkIn, bps)

    private def input(ref: TxOutRef, lovelace: BigInt, addr: Address = scriptAddr) =
        TxInInfo(
          ref,
          TxOut(addr, Value.lovelace(lovelace), OutputDatum.NoOutputDatum, prelude.Option.None)
        )
    private def out(h: PubKeyHash, lovelace: BigInt) =
        TxOut(pkAddr(h), Value.lovelace(lovelace), OutputDatum.NoOutputDatum, prelude.Option.None)

    private def fin(t: BigInt, incl: Boolean = true) =
        IntervalBound(IntervalBoundType.Finite(t), incl)
    private def range(l: Option[BigInt], u: Option[BigInt]) =
        Interval(
          l.fold(IntervalBound.negInf)(fin(_)),
          u.fold(IntervalBound.posInf)(fin(_, false))
        )

    private def run(
        action: Action,
        b: Booking = booking(),
        deposit: BigInt = 100_000_000,
        extraInputs: Seq[TxInInfo] = Seq.empty,
        outputs: Seq[TxOut] = Seq.empty,
        signers: Seq[PubKeyHash] = Seq.empty,
        valid: Interval = Interval.always,
        datum: prelude.Option[Data] = null,
        redeemer: Data = null,
        ref: TxOutRef = ownRef
    ): Boolean = {
        val inputs = Seq(input(ownRef, deposit)) ++ extraInputs
        val tx = TxInfo(
          inputs = prelude.List.from(inputs),
          outputs = prelude.List.from(outputs),
          validRange = valid,
          signatories = prelude.List.from(signers),
          id = tid(9)
        )
        val d = if datum == null then prelude.Option.Some(b.toData) else datum
        val r = if redeemer == null then action.toData else redeemer
        val ctx = ScriptContext(tx, r, ScriptInfo.SpendingScript(ref, d))
        contract.program.runWithDebug(ctx).isSuccess
    }

    private def cancel(
        u: Option[BigInt],
        outputs: Seq[TxOut] = Seq.empty,
        b: Booking = booking(),
        deposit: BigInt = 100_000_000,
        signers: Seq[PubKeyHash] = Seq(guest)
    ) = run(
      Action.Cancel,
      b,
      deposit,
      outputs = outputs,
      signers = signers,
      valid = range(Some(BigInt(0)), u)
    )

    // ---- common checks ----
    test("SP-1 missing datum fails") { // spec [SP-1]
        assert(
          !run(Action.Release, datum = prelude.Option.None, signers = Seq(host), outputs = Seq(out(guest, 100_000_000)))
        )
    }
    test("SP-2 bad datum fails") { // spec [SP-2]
        assert(!run(Action.Release, datum = prelude.Option.Some(BigInt(1).toData), signers = Seq(host), outputs = Seq(out(guest, 100_000_000))))
    }
    test("SP-3 bad redeemer fails") { // spec [SP-3]
        assert(!run(Action.Release, redeemer = BigInt(7).toData, signers = Seq(host), outputs = Seq(out(guest, 100_000_000))))
    }
    test("SP-4 ownRef matching no input fails") { // spec [SP-4]
        assert(!run(Action.Release, ref = otherRef, signers = Seq(host), outputs = Seq(out(guest, 100_000_000))))
    }
    test("SP-5 SP-6 two deposit inputs fail for every action") { // spec [SP-5] [SP-6]
        val extra = Seq(input(otherRef, 100_000_000))
        assert(!run(Action.Release, extraInputs = extra, signers = Seq(host), outputs = Seq(out(guest, 200_000_000))))
        assert(!run(Action.Claim, extraInputs = extra, signers = Seq(host), valid = range(Some(BigInt(1_121_600_001L)), None)))
        assert(!run(Action.Cancel, extraInputs = extra, signers = Seq(guest), valid = range(None, Some(cancelBy))))
    }
    test("SP-5 non-deposit extra inputs are fine") { // spec [SP-5]
        val extra = Seq(input(otherRef, 5_000_000, pkAddr(other)))
        assert(run(Action.Release, extraInputs = extra, signers = Seq(host), outputs = Seq(out(guest, 100_000_000))))
    }
    test("SP-7 deposit is own input lovelace only") { // spec [SP-7]
        assert(!run(Action.Release, deposit = 50_000_000, signers = Seq(host), outputs = Seq(out(guest, 49_999_999))))
        assert(run(Action.Release, deposit = 50_000_000, signers = Seq(host), outputs = Seq(out(guest, 50_000_000))))
    }

    // ---- cancel ----
    test("CA-1 guest signature required") { // spec [CA-1]
        assert(!cancel(Some(cancelBy), signers = Seq(host)))
        assert(!cancel(Some(cancelBy), signers = Seq.empty))
    }
    test("CA-2 infinite U fails") { // spec [CA-2]
        assert(!cancel(None))
    }
    test("CA-3 L ignored") { // spec [CA-3]
        assert(run(Action.Cancel, signers = Seq(guest), valid = Interval(IntervalBound.negInf, fin(cancelBy, false))))
        assert(run(Action.Cancel, signers = Seq(guest), valid = range(Some(BigInt(9_999_999_999L)), Some(cancelBy))))
    }
    test("CA-4 CA-15 free cancel at U == cancelBy needs no outputs") { // spec [CA-4] [CA-15]
        assert(cancel(Some(cancelBy)))
        assert(cancel(Some(cancelBy - 5)))
    }
    test("CA-6 limits") { // spec [CA-6]
        val fee = out(host, 10_000_000)
        assert(cancel(Some(cancelBy + 1), Seq(fee)))
        assert(cancel(Some(BigInt(1_096_400_000L)), Seq(fee)))
        assert(!cancel(Some(BigInt(1_096_400_001L)), Seq(fee)))
    }
    test("CA-5 free cancel tested first even if cancelBy after late limit") { // spec [CA-5]
        val b = booking().copy(cancelBy = BigInt(1_099_000_000L))
        assert(cancel(Some(BigInt(1_098_000_000L)), b = b))
        assert(!cancel(Some(BigInt(1_099_000_001L)), b = b, outputs = Seq(out(host, 10_000_000))))
    }
    // spec [CA-7] [CA-8] [CA-9] [CA-10] [CA-11] [CA-12]: normative vectors
    private val vectors = Seq(
      (BigInt(25_000_001), BigInt(1000), BigInt(2_500_001)),
      (BigInt(100_000_000), BigInt(9000), BigInt(50_000_000)),
      (BigInt(100_000_000), BigInt(0), BigInt(2_000_000)),
      (BigInt(10_000_000), BigInt(1000), BigInt(2_000_000)),
      (BigInt(1_500_000), BigInt(1000), BigInt(1_500_000)),
      (BigInt(1_500_000), BigInt(0), BigInt(1_500_000)),
      (BigInt(100_000_000), BigInt(-5), BigInt(2_000_000)), // [CA-8]
      (BigInt(100_000_000), BigInt(5000), BigInt(50_000_000)) // [CA-7] boundary
    )
    for ((dep, bps, fee) <- vectors) {
        test(s"CA-7..12 fee vector deposit=$dep bps=$bps fee=$fee") {
            // spec [CA-7] [CA-8] [CA-9] [CA-10] [CA-11] [CA-12] [CA-13]
            val u = Some(cancelBy + 1)
            val b = booking(bps)
            assert(cancel(u, Seq(out(host, fee)), b, dep))
            assert(!cancel(u, Seq(out(host, fee + 1)), b, dep))
            if fee > 0 then assert(!cancel(u, Seq(out(host, fee - 1)), b, dep))
        }
    }
    test("CA-13 split host outputs sum; staking part ignored; others not counted") { // spec [CA-13]
        val b = booking()
        val u = Some(cancelBy + 1)
        val staked = TxOut(
          Address(Credential.PubKeyCredential(host), prelude.Option.Some(StakingCredential.StakingHash(Credential.PubKeyCredential(other)))),
          Value.lovelace(500_001), OutputDatum.NoOutputDatum, prelude.Option.None)
        assert(cancel(u, Seq(out(host, 10_000_000), staked), b, 100_000_000).equals(false)) // fee is 10_000_000
        assert(cancel(u, Seq(out(host, 9_499_999), staked), b, 100_000_000))
        val scriptOut = TxOut(scriptAddr.copy(credential = Credential.ScriptCredential(host.hash)), Value.lovelace(10_000_000), OutputDatum.NoOutputDatum, prelude.Option.None)
        assert(!cancel(u, Seq(scriptOut), b, 100_000_000))
    }
    test("CA-14 guest outputs not checked on late cancel") { // spec [CA-14]
        assert(cancel(Some(cancelBy + 1), Seq(out(host, 10_000_000)))) // guest gets nothing
    }
    test("CA-13 zero fee needs zero host output") { // spec [CA-13]
        assert(cancel(Some(cancelBy + 1), Seq.empty, deposit = 0))
        assert(!cancel(Some(cancelBy + 1), Seq(out(host, 1)), deposit = 0))
    }

    // ---- release ----
    test("RE-1 host signature required") { // spec [RE-1]
        assert(!run(Action.Release, signers = Seq(guest), outputs = Seq(out(guest, 100_000_000))))
    }
    test("RE-2 RE-4 guest sum at least deposit") { // spec [RE-2] [RE-4]
        assert(run(Action.Release, signers = Seq(host), outputs = Seq(out(guest, 100_000_000))))
        assert(run(Action.Release, signers = Seq(host), outputs = Seq(out(guest, 120_000_000))))
        assert(!run(Action.Release, signers = Seq(host), outputs = Seq(out(guest, 99_999_999))))
        assert(run(Action.Release, signers = Seq(host), outputs = Seq(out(guest, 60_000_000), out(guest, 40_000_000))))
    }
    test("RE-3 validity range not checked") { // spec [RE-3]
        assert(run(Action.Release, signers = Seq(host), outputs = Seq(out(guest, 100_000_000)), valid = Interval.never))
    }

    // ---- claim ----
    private val claimLimit = BigInt(1_121_600_000L)
    private def claim(l: Option[BigInt], u: Option[BigInt] = None, signers: Seq[PubKeyHash] = Seq(host)) =
        run(Action.Claim, signers = signers, valid = range(l, u))
    test("CL-1 host signature required") { // spec [CL-1]
        assert(!claim(Some(claimLimit + 1), signers = Seq(guest)))
    }
    test("CL-2 infinite L fails") { // spec [CL-2]
        assert(!claim(None))
    }
    test("CL-3 strict grace limit") { // spec [CL-3]
        assert(!claim(Some(claimLimit)))
        assert(claim(Some(claimLimit + 1)))
    }
    test("CL-4 CL-5 U ignored, outputs not checked") { // spec [CL-4] [CL-5]
        assert(claim(Some(claimLimit + 1), Some(BigInt(1))))
        assert(claim(Some(claimLimit + 1), None))
    }
}
