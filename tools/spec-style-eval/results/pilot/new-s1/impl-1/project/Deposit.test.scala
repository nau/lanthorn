package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.{ByteString, Data}
import scalus.uplc.builtin.Data.toData
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.v2.OutputDatum
import scalus.cardano.onchain.plutus.v1.{
    Credential,
    Interval,
    IntervalBound,
    IntervalBoundType,
    Closure
}
import scalus.cardano.onchain.plutus.prelude.{
    AssocMap,
    SortedMap,
    List as PList,
    Option as POption
}
import scalus.testing.kit.ScalusTest

class DepositTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default
    private val contract = PlutusV3.compile(Deposit.validate)

    private val guest = PubKeyHash(ByteString.fromHex("11" * 28))
    private val host = PubKeyHash(ByteString.fromHex("22" * 28))
    private val other = PubKeyHash(ByteString.fromHex("33" * 28))
    private val scriptCred =
        Credential.ScriptCredential(ByteString.fromHex("aa" * 28))
    private val scriptAddr = Address(scriptCred, POption.None)
    private def pk(h: PubKeyHash) =
        Address(Credential.PubKeyCredential(h), POption.None)

    private val cancelBy = BigInt(1_000_000_000)
    private val checkIn = BigInt(1_100_000_000)
    private val booking = Booking(guest, host, cancelBy, checkIn, BigInt(1000))

    private val ownRef = TxOutRef(TxId(ByteString.fromHex("01" * 32)), 0)
    private val otherRef = TxOutRef(TxId(ByteString.fromHex("02" * 32)), 0)

    private def out(a: Address, lovelace: BigInt) =
        TxOut(
          a,
          Value.lovelace(lovelace),
          OutputDatum.NoOutputDatum,
          POption.None
        )

    private def fin(t: BigInt, inclusive: Boolean = true) =
        IntervalBound(IntervalBoundType.Finite(t), inclusive)
    private val negInf = IntervalBound(IntervalBoundType.NegInf, true)
    private val posInf = IntervalBound(IntervalBoundType.PosInf, true)

    private def run(
        action: Action,
        deposit: BigInt = 100_000_000,
        bk: Booking = booking,
        from: IntervalBound = negInf,
        to: IntervalBound = posInf,
        signers: Seq[PubKeyHash] = Seq.empty,
        outputs: Seq[TxOut] = Seq.empty,
        extraInputs: Seq[TxInInfo] = Seq.empty,
        withOwnInput: Boolean = true,
        datum: POption[Data] = null,
        redeemer: Data = null
    ): Boolean = {
        val own = TxInInfo(ownRef, out(scriptAddr, deposit))
        val inputs =
            (if withOwnInput then Seq(own) else Seq.empty) ++ extraInputs
        val d = if datum == null then POption.Some(bk.toData) else datum
        val r = if redeemer == null then action.toData else redeemer
        val info = TxInfo(
          inputs = PList.from(inputs),
          referenceInputs = PList.Nil,
          outputs = PList.from(outputs),
          fee = BigInt(0),
          mint = Value.zero,
          certificates = PList.Nil,
          withdrawals = SortedMap.empty,
          validRange = Interval(from, to),
          signatories = PList.from(signers),
          redeemers = AssocMap.empty,
          data = SortedMap.empty,
          id = TxId(ByteString.fromHex("ff" * 32)),
          votes = SortedMap.empty,
          proposalProcedures = PList.Nil,
          currentTreasuryAmount = POption.None,
          treasuryDonation = POption.None
        )
        val ctx = ScriptContext(info, r, ScriptInfo.SpendingScript(ownRef, d))
        contract.program.runWithDebug(ctx).isSuccess
    }

    private def lateCancel(
        u: BigInt,
        deposit: BigInt,
        bps: BigInt,
        hostPay: Seq[BigInt]
    ) = run(
      Action.Cancel,
      deposit,
      booking.copy(feeBps = bps),
      to = fin(u),
      signers = Seq(guest),
      outputs = hostPay.map(out(pk(host), _))
    )

    // ---- common checks
    test("SP-1 missing datum fails") { // spec [SP-1]
        assert(!run(Action.Release, signers = Seq(host), datum = POption.None))
    }
    test("SP-2 bad datum fails") { // spec [SP-2]
        assert(
          !run(
            Action.Release,
            signers = Seq(host),
            datum = POption.Some(BigInt(5).toData)
          )
        )
    }
    test("SP-3 bad redeemer fails") { // spec [SP-3]
        assert(
          !run(Action.Release, signers = Seq(host), redeemer = BigInt(9).toData)
        )
    }
    test("SP-4 own ref not among inputs fails") { // spec [SP-4]
        assert(
          !run(
            Action.Release,
            signers = Seq(host),
            outputs = Seq(out(pk(guest), 100_000_000)),
            withOwnInput = false,
            extraInputs = Seq(TxInInfo(otherRef, out(scriptAddr, 1)))
          )
        )
    }
    test(
      "SP-5 SP-6 two deposit inputs fail for every action"
    ) { // spec [SP-5] [SP-6]
        val two = Seq(TxInInfo(otherRef, out(scriptAddr, 100_000_000)))
        assert(
          !run(
            Action.Release,
            signers = Seq(host),
            outputs = Seq(out(pk(guest), 200_000_000)),
            extraInputs = two
          )
        )
        assert(
          !run(
            Action.Cancel,
            signers = Seq(guest),
            to = fin(cancelBy),
            extraInputs = two
          )
        )
        assert(
          !run(
            Action.Claim,
            signers = Seq(host),
            from = fin(checkIn + 21_600_001),
            extraInputs = two
          )
        )
    }
    test("SP-5 non-deposit extra inputs are fine") { // spec [SP-5]
        val extra = Seq(TxInInfo(otherRef, out(pk(other), 5_000_000)))
        assert(
          run(
            Action.Cancel,
            signers = Seq(guest),
            to = fin(cancelBy),
            extraInputs = extra
          )
        )
    }
    test("SP-7 deposit is lovelace of own input") { // spec [SP-7]
        assert(
          run(
            Action.Release,
            7_000_000,
            signers = Seq(host),
            outputs = Seq(out(pk(guest), 7_000_000))
          )
        )
        assert(
          !run(
            Action.Release,
            7_000_000,
            signers = Seq(host),
            outputs = Seq(out(pk(guest), 6_999_999))
          )
        )
    }
    test(
      "SP-9 datum with cancelBy after late limit is still accepted"
    ) { // spec [SP-9]
        val bk = booking.copy(cancelBy = checkIn + 5)
        assert(
          run(
            Action.Cancel,
            bk = bk,
            signers = Seq(guest),
            to = fin(checkIn + 5)
          )
        )
    }

    // ---- Cancel
    test("CA-1 guest must sign") { // spec [CA-1]
        assert(!run(Action.Cancel, to = fin(cancelBy), signers = Seq(host)))
        assert(run(Action.Cancel, to = fin(cancelBy), signers = Seq(guest)))
    }
    test("CA-2 infinite U fails") { // spec [CA-2]
        assert(!run(Action.Cancel, signers = Seq(guest), to = posInf))
    }
    test("CA-3 L ignored") { // spec [CA-3]
        assert(
          run(
            Action.Cancel,
            signers = Seq(guest),
            from = negInf,
            to = fin(cancelBy)
          )
        )
        assert(
          run(
            Action.Cancel,
            signers = Seq(guest),
            from = fin(5_000_000_000L),
            to = fin(cancelBy)
          )
        )
    }
    test(
      "CA-4 CA-15 free cancel needs no outputs; boundary U == cancelBy"
    ) { // spec [CA-4] [CA-15]
        assert(run(Action.Cancel, signers = Seq(guest), to = fin(cancelBy)))
        assert(
          run(Action.Cancel, signers = Seq(guest), to = fin(cancelBy, false))
        )
    }
    test("CA-5 CA-6 time table") { // spec [CA-5] [CA-6]
        assert(lateCancel(1_000_000_001, 100_000_000, 1000, Seq(10_000_000)))
        assert(!lateCancel(1_000_000_001, 100_000_000, 1000, Seq.empty))
        assert(lateCancel(1_096_400_000L, 100_000_000, 1000, Seq(10_000_000)))
        assert(!lateCancel(1_096_400_001L, 100_000_000, 1000, Seq(10_000_000)))
    }
    test(
      "CA-7..CA-12 fee vectors"
    ) { // spec [CA-7] [CA-8] [CA-9] [CA-10] [CA-11] [CA-12]
        val vs = Seq(
          (BigInt(25_000_001), BigInt(1000), BigInt(2_500_001)),
          (BigInt(100_000_000), BigInt(9000), BigInt(50_000_000)),
          (BigInt(100_000_000), BigInt(0), BigInt(2_000_000)),
          (BigInt(10_000_000), BigInt(1000), BigInt(2_000_000)),
          (BigInt(1_500_000), BigInt(1000), BigInt(1_500_000)),
          (BigInt(1_500_000), BigInt(0), BigInt(1_500_000)),
          (
            BigInt(100_000_000),
            BigInt(-7),
            BigInt(2_000_000)
          ) // negative bps -> 0
        )
        for (dep, bps, fee) <- vs do {
            assert(
              lateCancel(1_050_000_000L, dep, bps, Seq(fee)),
              s"$dep $bps $fee"
            )
            assert(
              !lateCancel(1_050_000_000L, dep, bps, Seq(fee + 1)),
              s"$dep $bps +1"
            )
            assert(
              !lateCancel(1_050_000_000L, dep, bps, Seq(fee - 1)),
              s"$dep $bps -1"
            )
        }
    }
    test(
      "CA-13 host output sum over several outputs; staking part ignored"
    ) { // spec [CA-13]
        assert(
          lateCancel(1_050_000_000L, 25_000_001, 1000, Seq(2_000_000, 500_001))
        )
        assert(!lateCancel(1_050_000_000L, 25_000_001, 1000, Seq(2_500_002)))
        assert(!lateCancel(1_050_000_000L, 25_000_001, 1000, Seq(2_500_000)))
        val staked = Address(
          Credential.PubKeyCredential(host),
          POption.Some(
            StakingCredential.StakingHash(Credential.PubKeyCredential(other))
          )
        )
        assert(
          run(
            Action.Cancel,
            25_000_001,
            to = fin(1_050_000_000L),
            signers = Seq(guest),
            outputs = Seq(
              TxOut(
                staked,
                Value.lovelace(2_500_001),
                OutputDatum.NoOutputDatum,
                POption.None
              )
            )
          )
        )
        // script-address output does not count
        assert(
          !run(
            Action.Cancel,
            25_000_001,
            to = fin(1_050_000_000L),
            signers = Seq(guest),
            outputs = Seq(out(scriptAddr, 2_500_001))
          )
        )
    }
    test("CA-14 guest output not checked on late cancel") { // spec [CA-14]
        assert(
          run(
            Action.Cancel,
            100_000_000,
            to = fin(1_050_000_000L),
            signers = Seq(guest),
            outputs = Seq(out(pk(host), 10_000_000))
          )
        )
    }

    // ---- Release
    test("RE-1 host must sign") { // spec [RE-1]
        val o = Seq(out(pk(guest), 100_000_000))
        assert(!run(Action.Release, signers = Seq(guest), outputs = o))
        assert(run(Action.Release, signers = Seq(host), outputs = o))
    }
    test("RE-2 RE-4 guest sum at least deposit") { // spec [RE-2] [RE-4]
        assert(
          run(
            Action.Release,
            signers = Seq(host),
            outputs = Seq(out(pk(guest), 120_000_000))
          )
        )
        assert(
          !run(
            Action.Release,
            signers = Seq(host),
            outputs = Seq(out(pk(guest), 99_999_999))
          )
        )
        assert(
          run(
            Action.Release,
            signers = Seq(host),
            outputs =
                Seq(out(pk(guest), 60_000_000), out(pk(guest), 40_000_000))
          )
        )
    }
    test("RE-3 validity range ignored") { // spec [RE-3]
        val o = Seq(out(pk(guest), 100_000_000))
        assert(
          run(
            Action.Release,
            signers = Seq(host),
            outputs = o,
            from = fin(5),
            to = fin(6)
          )
        )
    }

    // ---- Claim
    test("CL-1 host must sign") { // spec [CL-1]
        assert(
          !run(
            Action.Claim,
            signers = Seq(guest),
            from = fin(checkIn + 21_600_001)
          )
        )
    }
    test("CL-2 infinite L fails") { // spec [CL-2]
        assert(!run(Action.Claim, signers = Seq(host), from = negInf))
    }
    test("CL-3 strict grace boundary") { // spec [CL-3]
        assert(
          !run(Action.Claim, signers = Seq(host), from = fin(1_121_600_000L))
        )
        assert(
          run(Action.Claim, signers = Seq(host), from = fin(1_121_600_001L))
        )
    }
    test("CL-4 CL-5 U ignored, no output check") { // spec [CL-4] [CL-5]
        assert(
          run(
            Action.Claim,
            signers = Seq(host),
            from = fin(1_121_600_001L),
            to = posInf
          )
        )
        assert(
          run(
            Action.Claim,
            signers = Seq(host),
            from = fin(1_121_600_001L),
            to = fin(1)
          )
        )
    }
}
