package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.ByteString
import scalus.uplc.builtin.Data
import scalus.uplc.eval.*
import scalus.uplc.builtin.Data.toData
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.v2.OutputDatum
import scalus.cardano.onchain.plutus.prelude.{List as PList, Option as POption}

class DepositTest extends AnyFunSuite, scalus.testing.kit.ScalusTest {

    private given Options = Options.default
    private val contract = PlutusV3.compile(Deposit.validate)

    private val guest = PubKeyHash(ByteString.fromHex("aa" * 28))
    private val host = PubKeyHash(ByteString.fromHex("bb" * 28))
    private val other = PubKeyHash(ByteString.fromHex("cc" * 28))
    private val scriptHash = ByteString.fromHex("dd" * 28)
    private val scriptAddr = Address(Credential.ScriptCredential(scriptHash), POption.None)
    private val cancelBy = BigInt(1_700_000_000_000L)
    private val checkIn = BigInt(1_700_100_000_000L)
    private val cutoff = checkIn - 3_600_000
    private val grace = checkIn + 21_600_000

    private def booking(feeBps: BigInt = 1000, cb: BigInt = cancelBy) =
        Booking(guest, host, cb, checkIn, feeBps)

    private def pkAddr(k: PubKeyHash, stake: Boolean = false) =
        Address(
          Credential.PubKeyCredential(k),
          if stake then
              POption.Some(
                StakingCredential.StakingHash(Credential.PubKeyCredential(other))
              )
          else POption.None
        )

    private def out(a: Address, lovelace: BigInt, token: Boolean = false) =
        TxOut(
          a,
          if token then Value.lovelace(lovelace) + Value(ByteString.fromHex("ee" * 28), ByteString.fromString("t"), BigInt(5))
          else Value.lovelace(lovelace),
          OutputDatum.NoOutputDatum,
          POption.None
        )

    private def ref(i: Int) = TxOutRef(TxId(ByteString.fromHex("11" * 32)), BigInt(i))
    private def depIn(i: Int, lovelace: BigInt) = TxInInfo(ref(i), out(scriptAddr, lovelace))

    private def lo(t: BigInt) = IntervalBound.finiteInclusive(t)
    private def hi(t: BigInt) = IntervalBound.finiteExclusive(t)

    private def run(
        action: Action,
        deposit: BigInt = BigInt(25_000_001),
        b: Booking = booking(),
        signers: Seq[PubKeyHash] = Seq.empty,
        range: Interval = Interval.always,
        outputs: Seq[TxOut] = Seq.empty,
        extraInputs: Seq[TxInInfo] = Seq.empty,
        datum: Boolean = true,
        rawDatum: Option[Data] = None
    ): Boolean = {
        val tx = TxInfo.placeholder.copy(
          inputs = PList.from(depIn(0, deposit) +: extraInputs),
          outputs = PList.from(outputs),
          validRange = range,
          signatories = PList.from(signers)
        )
        val d = rawDatum.getOrElse(b.toData)
        val ctx = ScriptContext(
          tx,
          action.toData,
          ScriptInfo.SpendingScript(ref(0), if datum then POption.Some(d) else POption.None)
        )
        contract.program.runWithDebug(ctx).isSuccess
    }

    private def cancelAt(
        u: BigInt,
        hostPays: BigInt,
        deposit: BigInt = BigInt(25_000_001),
        b: Booking = booking(),
        signers: Seq[PubKeyHash] = Seq(guest)
    ) =
        run(Action.Cancel, deposit, b, signers,
          Interval(lo(u - 600_000), hi(u)),
          if hostPays > 0 then Seq(out(pkAddr(host), hostPays)) else Seq.empty)

    // ---- GEN ----
    test("[GEN-2] missing datum rejected") {
        // spec [GEN-2]
        assert(!run(Action.Release, signers = Seq(host), outputs = Seq(out(pkAddr(guest), 25_000_001)), datum = false))
    }
    test("[GEN-1] undecodable datum rejected") {
        // spec [GEN-1]
        assert(!run(Action.Release, signers = Seq(host), rawDatum = Some(BigInt(1).toData),
          outputs = Seq(out(pkAddr(guest), 25_000_001))))
    }
    test("[GEN-3] two deposit inputs rejected") {
        // spec [GEN-3]
        assert(!run(Action.Release, signers = Seq(host), outputs = Seq(out(pkAddr(guest), 100_000_000)),
          extraInputs = Seq(depIn(1, 25_000_001))))
    }
    test("[GEN-3] non-deposit extra input is fine") {
        // spec [GEN-3]
        val ex = TxInInfo(ref(5), out(pkAddr(other), 5_000_000))
        assert(run(Action.Release, signers = Seq(host), outputs = Seq(out(pkAddr(guest), 25_000_001)),
          extraInputs = Seq(ex)))
    }
    test("[GEN-4][GEN-5] deposit is lovelace only; other assets ignored") {
        // spec [GEN-4] [GEN-5]
        val in = TxInInfo(ref(0), out(scriptAddr, 25_000_001, token = true))
        val tx = TxInfo.placeholder.copy(
          inputs = PList(in),
          outputs = PList(out(pkAddr(guest), 25_000_001, token = false)),
          signatories = PList(host))
        val ctx = ScriptContext(tx, Action.Release.toData,
          ScriptInfo.SpendingScript(ref(0), POption.Some(booking().toData)))
        assert(contract.program.runWithDebug(ctx).isSuccess)
    }
    test("[GEN-8] guest == host: same checks") {
        // spec [GEN-8]
        val b = Booking(guest, guest, cancelBy, checkIn, BigInt(1000))
        assert(run(Action.Release, b = b, signers = Seq(guest), outputs = Seq(out(pkAddr(guest), 25_000_001))))
        assert(!run(Action.Release, b = b, signers = Seq(guest), outputs = Seq(out(pkAddr(guest), 25_000_000))))
    }

    // ---- CANCEL ----
    test("[CAN-1] cancel needs guest signature") {
        // spec [CAN-1]
        assert(!cancelAt(cancelBy, 0, signers = Seq(host)))
        assert(!cancelAt(cancelBy, 0, signers = Seq.empty))
        assert(cancelAt(cancelBy, 0))
    }
    test("[CAN-2] no upper bound rejected") {
        // spec [CAN-2] [GEN-7]
        assert(!run(Action.Cancel, signers = Seq(guest), range = Interval.after(cancelBy - 10)))
    }
    test("[CAN-3][CAN-4] free cancel boundary, no output check, bad datum ok") {
        // spec [CAN-3] [CAN-4]
        assert(cancelAt(cancelBy, 0))
        assert(cancelAt(cancelBy, 0, b = booking(feeBps = BigInt(-5))))
    }
    test("[CAN-3] free window wins when cancelBy later than cutoff") {
        // spec [CAN-3] [CAN-5]
        val b = booking(cb = BigInt(1_700_098_000_000L))
        assert(cancelAt(BigInt(1_700_098_000_000L), 0, b = b))
        assert(!cancelAt(BigInt(1_700_098_000_001L), 2_500_001, b = b))
    }
    test("[CAN-5] cutoff boundary") {
        // spec [CAN-5]
        assert(cancelAt(cutoff, 2_500_001))
        assert(!cancelAt(cutoff + 1, 2_500_001))
    }
    test("[CAN-7][CAN-10] fee exact, ceiling") {
        // spec [CAN-7] [CAN-10]
        assert(cancelAt(cancelBy + 1, 2_500_001))
        assert(!cancelAt(cancelBy + 1, 2_500_000))
        assert(!cancelAt(cancelBy + 1, 2_500_002))
        assert(!cancelAt(cancelBy + 1, 0))
    }
    test("[CAN-6] fee rate capped at 5000") {
        // spec [CAN-6]
        assert(cancelAt(cancelBy + 1, 12_500_001, b = booking(9000)))
        assert(cancelAt(cancelBy + 1, 12_500_001, b = booking(5000)))
        assert(!cancelAt(cancelBy + 1, 22_500_001, b = booking(9000)))
    }
    test("[CAN-8] minimum fee 2 ADA") {
        // spec [CAN-8]
        assert(cancelAt(cancelBy + 1, 2_000_000, deposit = 10_000_000, b = booking(0)))
        assert(!cancelAt(cancelBy + 1, 0, deposit = 10_000_000, b = booking(0)))
    }
    test("[CAN-9] fee capped at deposit, last step") {
        // spec [CAN-9]
        assert(cancelAt(cancelBy + 1, 1_500_000, deposit = 1_500_000, b = booking(0)))
        assert(!cancelAt(cancelBy + 1, 2_000_000, deposit = 1_500_000, b = booking(0)))
    }
    test("[CAN-7] fee table rows") {
        // spec [CAN-7]
        assert(cancelAt(cancelBy + 1, 10_000_000, deposit = 100_000_000, b = booking(1000)))
    }
    test("[CAN-6][CAN-8] negative feeBps gives min fee") {
        // spec [CAN-6] [CAN-8]
        assert(cancelAt(cancelBy + 1, 2_000_000, b = booking(-100)))
    }
    test("[CAN-10] host sum counts several outputs and ignores stake part") {
        // spec [CAN-10]
        val outs = Seq(out(pkAddr(host), 1_000_000), out(pkAddr(host, stake = true), 1_500_001))
        assert(run(Action.Cancel, signers = Seq(guest),
          range = Interval(lo(cancelBy), hi(cancelBy + 1)), outputs = outs))
    }
    test("[CAN-11] guest outputs not checked") {
        // spec [CAN-11]
        assert(run(Action.Cancel, signers = Seq(guest), range = Interval(lo(cancelBy), hi(cancelBy + 1)),
          outputs = Seq(out(pkAddr(host), 2_500_001))))
    }

    // ---- RELEASE ----
    test("[REL-1] release needs host signature") {
        // spec [REL-1]
        val o = Seq(out(pkAddr(guest), 25_000_001))
        assert(!run(Action.Release, signers = Seq(guest), outputs = o))
        assert(run(Action.Release, signers = Seq(host), outputs = o))
    }
    test("[REL-2] no time check") {
        // spec [REL-2]
        val o = Seq(out(pkAddr(guest), 25_000_001))
        assert(run(Action.Release, signers = Seq(host), outputs = o, range = Interval.never))
    }
    test("[REL-3] guest sum at least deposit") {
        // spec [REL-3]
        assert(!run(Action.Release, signers = Seq(host), outputs = Seq(out(pkAddr(guest), 25_000_000))))
        assert(run(Action.Release, signers = Seq(host),
          outputs = Seq(out(pkAddr(guest), 20_000_000), out(pkAddr(guest), 5_000_001))))
        assert(run(Action.Release, signers = Seq(host), outputs = Seq(out(pkAddr(guest, stake = true), 25_000_001))))
        assert(run(Action.Release, signers = Seq(host), outputs = Seq(out(pkAddr(guest), 30_000_000))))
    }
    test("[REL-4] other outputs unlimited") {
        // spec [REL-4]
        assert(run(Action.Release, signers = Seq(host),
          outputs = Seq(out(pkAddr(guest), 25_000_001), out(pkAddr(host), 99_000_000))))
    }

    // ---- CLAIM ----
    private def claim(l: Option[BigInt], u: Option[BigInt], signers: Seq[PubKeyHash] = Seq(host)) =
        run(Action.Claim, signers = signers, range = Interval(
          l.map(lo).getOrElse(IntervalBound.negInf), u.map(hi).getOrElse(IntervalBound.posInf)))

    test("[CLM-1] claim needs host signature") {
        // spec [CLM-1]
        assert(!claim(Some(grace + 1), None, Seq(guest)))
    }
    test("[CLM-2][GEN-7] strict lower bound; absent rejected") {
        // spec [CLM-2] [GEN-7]
        assert(!claim(None, None))
        assert(!claim(None, Some(grace + 100)))
        assert(!claim(Some(grace), None))
        assert(claim(Some(grace + 1), None))
    }
    test("[CLM-3] upper bound unused") {
        // spec [CLM-3]
        assert(claim(Some(grace + 1), Some(grace + 5)))
        assert(claim(Some(grace + 1), None))
    }
    test("[CLM-4] no outputs needed") {
        // spec [CLM-4]
        assert(claim(Some(grace + 1), None))
    }
}
