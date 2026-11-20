package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.ByteString
import scalus.uplc.builtin.Data
import scalus.uplc.builtin.Data.toData
import scalus.cardano.ledger.CardanoInfo
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.prelude.{List as PList, Option as POption, SortedMap, AssocMap}
import scalus.testing.kit.{ScalusTest, TestUtil}

class DepositTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default
    private given env: CardanoInfo = TestUtil.testEnvironment

    private val contract = PlutusV3.compile(Deposit.validate)

    private val guest = PubKeyHash(ByteString.fromHex("aa" * 28))
    private val host = PubKeyHash(ByteString.fromHex("bb" * 28))
    private val other = PubKeyHash(ByteString.fromHex("cc" * 28))
    private val scriptAddr =
        Address(Credential.ScriptCredential(ByteString.fromHex("dd" * 28)), POption.None)
    private def pkAddr(k: PubKeyHash) = Address(Credential.PubKeyCredential(k), POption.None)

    private val checkIn = BigInt(1_800_000_000_000L)
    private val cancelBy = BigInt(1_790_000_000_000L)
    private val booking = Booking(guest, host, cancelBy, checkIn, BigInt(1000))

    private def ada(n: BigInt) = Value.lovelace(n)
    private def out(a: Address, n: BigInt) =
        TxOut(a, ada(n), OutputDatum.NoOutputDatum, POption.None)
    private val ownRef = TxOutRef(TxId(ByteString.fromHex("11" * 32)), BigInt(0))
    private val otherRef = TxOutRef(TxId(ByteString.fromHex("22" * 32)), BigInt(0))

    private def fin(t: BigInt) = IntervalBound(IntervalBoundType.Finite(t), true)
    private val inf = IntervalBound(IntervalBoundType.PosInf, true)
    private val ninf = IntervalBound(IntervalBoundType.NegInf, true)
    private def range(l: Option[Long], u: Option[Long]) =
        Interval(l.fold(ninf)(x => fin(BigInt(x))), u.fold(inf)(x => fin(BigInt(x))))
    private def upTo(u: Long) = range(None, Some(u))

    private def run(
        action: Action,
        signers: Seq[PubKeyHash],
        validRange: Interval,
        outputs: Seq[TxOut],
        deposit: BigInt = BigInt(25_000_000),
        b: Booking = booking,
        extraInputs: Seq[TxInInfo] = Nil,
        datum: Option[Data] = Some(booking.toData),
        redeemer: Option[Data] = None,
        value: Option[Value] = None
    ): Boolean = {
        val own = TxInInfo(ownRef, TxOut(scriptAddr, value.getOrElse(ada(deposit)), OutputDatum.NoOutputDatum, POption.None))
        val txInfo = TxInfo(
          inputs = PList.from((own +: extraInputs).toList),
          referenceInputs = PList.Nil,
          outputs = PList.from(outputs.toList),
          fee = BigInt(0),
          mint = Value.zero,
          certificates = PList.Nil,
          withdrawals = SortedMap.empty,
          validRange = validRange,
          signatories = PList.from(signers.toList),
          redeemers = AssocMap.empty,
          data = SortedMap.empty,
          id = TxId(ByteString.fromHex("33" * 32)),
          votes = SortedMap.empty,
          proposalProcedures = PList.Nil,
          currentTreasuryAmount = POption.None,
          treasuryDonation = POption.None
        )
        val d = datum.map(x => if b eq booking then x else b.toData)
        val ctx = ScriptContext(
          txInfo,
          redeemer.getOrElse(action.toData),
          ScriptInfo.SpendingScript(ownRef, d.fold(POption.None: POption[Data])(POption.Some(_)))
        )
        contract.program.runWithDebug(ctx).isSuccess
    }

    private def doCancel(u: Option[Long], outs: Seq[TxOut], dep: BigInt = BigInt(25_000_000), b: Booking = booking, l: Option[Long] = None) =
        run(Action.Cancel, Seq(guest), range(l, u), outs, dep, b)

    // spec [CN-2] [CN-3] [CN-4] [CN-5] [CN-6] decision table
    test("Cancel decision table") {
        assert(!doCancel(None, Nil)) // CN-2
        assert(doCancel(Some(1_789_999_999_999L), Nil)) // CN-4
        assert(doCancel(Some(1_790_000_000_000L), Nil)) // CN-4
        val fee = out(pkAddr(host), 2_500_000)
        assert(doCancel(Some(1_790_000_000_001L), Seq(fee))) // CN-5
        assert(!doCancel(Some(1_790_000_000_001L), Nil)) // CN-5
        assert(doCancel(Some(1_799_996_400_000L), Seq(fee)))
        assert(!doCancel(Some(1_799_996_400_000L), Nil))
        assert(!doCancel(Some(1_799_996_400_001L), Seq(fee))) // CN-6
    }

    // spec [CN-3] L is ignored
    test("Cancel ignores lower bound") {
        assert(doCancel(Some(1_789_999_999_999L), Nil, l = Some(0L)))
        assert(doCancel(Some(1_789_999_999_999L), Nil, l = Some(9_999_999_999_999L)))
    }

    // spec [CN-4] precedes [CN-5]
    test("Cancel precedence vector") {
        val b = booking.copy(cancelBy = BigInt(1_799_999_000_000L))
        assert(doCancel(Some(1_799_998_000_000L), Nil, b = b))
    }

    // spec [CN-1]
    test("Cancel needs guest signature") {
        assert(!run(Action.Cancel, Seq(host), upTo(1), Nil))
        assert(!run(Action.Cancel, Seq(other), upTo(1), Nil))
        assert(!run(Action.Cancel, Nil, upTo(1), Nil))
        assert(run(Action.Cancel, Seq(other, guest), upTo(1), Nil))
    }

    // spec [CN-5] [CN-7] late cancel output vector
    test("Late Cancel output vector") {
        val dep = BigInt(25_000_001)
        val u = Some(1_795_000_000_000L)
        val h = pkAddr(host)
        assert(doCancel(u, Seq(out(h, 2_500_001)), dep))
        assert(doCancel(u, Seq(out(h, 1_000_000), out(h, 1_500_001)), dep))
        assert(doCancel(u, Seq(out(h, 2_500_001), out(pkAddr(guest), 5)), dep))
        assert(!doCancel(u, Seq(out(h, 2_500_000)), dep))
        assert(!doCancel(u, Seq(out(h, 2_500_002)), dep))
        assert(!doCancel(u, Seq(out(pkAddr(guest), 25_000_001)), dep))
        // script address outputs have no payment key hash
        assert(!doCancel(u, Seq(out(scriptAddr, 2_500_001)), dep))
    }

    // spec [CN-7] free cancel: any outputs
    test("Free Cancel constrains no output") {
        assert(doCancel(Some(1L), Seq(out(pkAddr(other), 1))))
    }

    // spec [FE-1] [FE-2] [FE-3] [FE-4] [FE-5] fee vectors (via Late Cancel)
    test("Fee vectors") {
        val u = Some(1_795_000_000_000L)
        def feeOk(dep: BigInt, bps: BigInt, fee: BigInt) = {
            val b = booking.copy(feeBps = bps)
            val h = pkAddr(host)
            assert(doCancel(u, Seq(out(h, fee)), dep, b), s"$dep $bps $fee")
            assert(!doCancel(u, Seq(out(h, fee + 1)), dep, b), s"$dep $bps +1")
            assert(!doCancel(u, Seq(out(h, fee - 1)), dep, b), s"$dep $bps -1")
        }
        feeOk(25_000_000, 1000, 2_500_000)
        feeOk(25_000_001, 1000, 2_500_001)
        feeOk(25_000_000, 9000, 12_500_000)
        feeOk(25_000_000, 0, 2_000_000)
        feeOk(25_000_000, -5, 2_000_000)
        feeOk(20_000_000, 1000, 2_000_000)
        feeOk(20_000_000, 999, 2_000_000)
        feeOk(10_000_000, 1000, 2_000_000)
        feeOk(1_500_000, 1000, 1_500_000)
    }

    private def release(g: Seq[BigInt], r: Interval = upTo(1), signers: Seq[PubKeyHash] = Seq(host)) =
        run(Action.Release, signers, r, g.map(n => out(pkAddr(guest), n)))

    // spec [RL-1] [RL-2] [RL-3] [RL-4] release vectors
    test("Release vectors") {
        assert(release(Seq(25_000_000)))
        assert(!release(Seq(24_999_999)))
        assert(release(Seq(30_000_000)))
        assert(release(Seq(10_000_000, 15_000_000)))
        assert(release(Seq(25_000_000), range(None, None))) // RL-2
        assert(!release(Seq(25_000_000), signers = Seq(guest))) // RL-1
        assert(!release(Nil))
        // RL-3: host outputs do not count
        assert(!run(Action.Release, Seq(host), upTo(1), Seq(out(pkAddr(host), 25_000_000))))
        // RL-4: extra outputs allowed
        assert(run(Action.Release, Seq(host), upTo(1), Seq(out(pkAddr(guest), 25_000_000), out(pkAddr(host), 7))))
    }

    private def claim(l: Option[Long], u: Option[Long] = None, signers: Seq[PubKeyHash] = Seq(host)) =
        run(Action.Claim, signers, range(l, u), Nil)

    // spec [CL-1] [CL-2] [CL-3] [CL-4] [CL-5] claim vectors
    test("Claim vectors") {
        assert(!claim(None)) // CL-2
        assert(!claim(None, Some(1L)))
        assert(!claim(Some(1_800_021_599_999L)))
        assert(!claim(Some(1_800_021_600_000L), Some(5L)))
        assert(claim(Some(1_800_021_600_001L))) // CL-3, CL-4
        assert(claim(Some(1_800_021_600_001L), Some(5L))) // CL-4
        assert(!claim(Some(1_800_021_600_001L), signers = Seq(guest))) // CL-1
    }

    // spec [SP-1]
    test("absent datum fails") {
        assert(!run(Action.Release, Seq(host), upTo(1), Seq(out(pkAddr(guest), 25_000_000)), datum = None))
    }

    // spec [SP-2]
    test("malformed datum fails") {
        assert(!run(Action.Release, Seq(host), upTo(1), Seq(out(pkAddr(guest), 25_000_000)), datum = Some(Data.I(BigInt(1)))))
    }

    // spec [SP-3]
    test("malformed redeemer fails") {
        assert(!run(Action.Release, Seq(host), upTo(1), Seq(out(pkAddr(guest), 25_000_000)), redeemer = Some(Data.I(BigInt(7)))))
    }

    // spec [SP-4]
    test("exactly one input at own address") {
        val second = TxInInfo(otherRef, TxOut(scriptAddr, ada(25_000_000), OutputDatum.NoOutputDatum, POption.None))
        val g = Seq(out(pkAddr(guest), 50_000_000))
        assert(!run(Action.Release, Seq(host), upTo(1), g, extraInputs = Seq(second)))
        val wallet = TxInInfo(otherRef, TxOut(pkAddr(other), ada(5_000_000), OutputDatum.NoOutputDatum, POption.None))
        assert(run(Action.Release, Seq(host), upTo(1), g, extraInputs = Seq(wallet)))
    }

    // spec [SP-5] [SP-9] only lovelace counts
    test("deposit is own input lovelace; other assets ignored") {
        assert(!release(Seq(24_999_999)))
        val v = ada(25_000_000) + Value(
          ByteString.fromHex("ee" * 28), ByteString.fromString("T"), BigInt(5))
        assert(run(Action.Release, Seq(host), upTo(1), Seq(out(pkAddr(guest), 25_000_000)), value = Some(v)))
    }

    // spec [SP-6] [SP-7] [SP-8] bounds are taken as written
    test("exclusive/inclusive flag is not adjusted") {
        val ex = Interval(ninf, IntervalBound(IntervalBoundType.Finite(cancelBy), false))
        assert(run(Action.Cancel, Seq(guest), ex, Nil))
        val exL = Interval(IntervalBound(IntervalBoundType.Finite(BigInt(1_800_021_600_000L)), false), inf)
        assert(!run(Action.Claim, Seq(host), exL, Nil))
    }

    // spec [EX-3] guest == host follows the same rules
    test("guest equals host") {
        val b = booking.copy(host = guest)
        assert(run(Action.Release, Seq(guest), upTo(1), Seq(out(pkAddr(guest), 25_000_000)), b = b))
        assert(!run(Action.Release, Seq(guest), upTo(1), Nil, b = b))
    }
}
