package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.Data.toData
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.prelude.*
import scalus.testing.kit.{ScalusTest, TestUtil}

/** Hidden tests of the spec-style eval. Written from `notes.md` before any spec existed.
  *
  * The implementer never sees this file. The harness copies it into a clean copy of the
  * project after the implementer process has exited.
  *
  * Each test name starts with its group: `[stated]`, `[boundary]` or `[intent]`. The harness
  * parses that prefix. Each test holds at least one context that the validator must accept
  * and one that it must reject, so a validator that rejects everything fails every test.
  *
  * Every context has the shape that the ledger produces: the own input is present and has an
  * inline datum, the lower validity bound is closed, the upper bound is open, and no address
  * has a staking part.
  */
class HiddenOracleTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default

    private val contract = PlutusV3.compile(Deposit.validate)

    private val guest: PubKeyHash = TestUtil.mockPubKeyHash(1)
    private val host: PubKeyHash = TestUtil.mockPubKeyHash(2)
    private val stranger: PubKeyHash = TestUtil.mockPubKeyHash(3)
    private val scriptHash: ValidatorHash = TestUtil.mockScriptHash(7)
    private val scriptAddress = Address(Credential.ScriptCredential(scriptHash), Option.None)

    private val ada: BigInt = 1_000_000
    private val hour: BigInt = 3_600_000
    private val day: BigInt = 24 * hour

    private val cancelBy: BigInt = BigInt(1_800_000_000_000L)
    private val checkIn: BigInt = cancelBy + 3 * day
    private val lockout: BigInt = hour
    private val grace: BigInt = 6 * hour

    private def booking(feeBps: BigInt = 1000): Booking =
        Booking(guest, host, cancelBy, checkIn, feeBps)

    /** Validity range `[lower, upper)`, as the ledger gives it. `None` means no bound. */
    private def range(
        lower: scala.Option[BigInt],
        upper: scala.Option[BigInt]
    ): Interval = Interval(
      lower.map(IntervalBound.finiteInclusive).getOrElse(IntervalBound.negInf),
      upper.map(IntervalBound.finiteExclusive).getOrElse(IntervalBound.posInf)
    )

    /** A transaction that ends at `upper`. It starts one hour earlier. */
    private def endsAt(upper: BigInt): Interval =
        range(scala.Some(upper - hour), scala.Some(upper))

    /** A transaction that starts at `lower`. It ends one hour later. */
    private def startsAt(lower: BigInt): Interval =
        range(scala.Some(lower), scala.Some(lower + hour))

    private def pay(to: PubKeyHash, lovelace: BigInt): TxOut =
        TxOut(Address(Credential.PubKeyCredential(to), Option.None), Value.lovelace(lovelace))

    private def depositInput(index: BigInt, lovelace: BigInt, datum: Booking): TxInInfo =
        TxInInfo(
          outRef = TestUtil.mockTxOutRef(10, index),
          resolved = TxOut(
            address = scriptAddress,
            value = Value.lovelace(lovelace),
            datum = OutputDatum.OutputDatum(datum.toData)
          )
        )

    /** Runs the validator for the first deposit input.
      *
      * @param deposits
      *   lovelace of each deposit input. All of them carry `datum`.
      */
    private def run(
        action: Action,
        signer: PubKeyHash,
        validRange: Interval,
        outputs: scala.Seq[TxOut],
        deposit: BigInt = 100 * ada,
        datum: Booking = booking(),
        extraDeposits: scala.Seq[BigInt] = scala.Seq.empty
    ): Boolean = {
        val own = depositInput(0, deposit, datum)
        val others = extraDeposits.zipWithIndex.map { case (lovelace, i) =>
            depositInput(BigInt(i + 1), lovelace, datum)
        }
        // The signer funds the transaction fee from a wallet input.
        val wallet = TxInInfo(
          outRef = TestUtil.mockTxOutRef(20, 0),
          resolved = pay(signer, 5 * ada)
        )
        val txInfo = TxInfo(
          inputs = List.from(own +: others :+ wallet),
          outputs = List.from(outputs),
          fee = BigInt(200_000),
          validRange = validRange,
          signatories = List(signer),
          id = TestUtil.mockTxOutRef(30, 0).id
        )
        val context = ScriptContext(
          txInfo = txInfo,
          redeemer = action.toData,
          scriptInfo = ScriptInfo.SpendingScript(
            txOutRef = own.outRef,
            datum = Option.Some(datum.toData)
          )
        )
        contract.program.runWithDebug(context).isSuccess
    }

    private def accepts(what: String)(result: Boolean): Unit =
        assert(result, s"- the validator must ACCEPT: $what")

    private def rejects(what: String)(result: Boolean): Unit =
        assert(!result, s"- the validator must REJECT: $what")

    // A time on each side of every limit, far from the limit itself.
    private val early = endsAt(cancelBy - day) // free cancel
    private val late = endsAt(cancelBy + day) // late cancel, fee applies
    private val afterGrace = startsAt(checkIn + grace + hour) // claim is open

    // 100 ADA deposit at 1000 bps: the fee is 10 ADA.
    private val fee10: BigInt = 10 * ada

    // ---------------------------------------------------------------------------------------
    // Group 1: stated rules
    // ---------------------------------------------------------------------------------------

    test("[stated] S1 Cancel needs the guest's signature") {
        accepts("free cancel signed by the guest") {
            run(Action.Cancel, guest, early, scala.Seq(pay(guest, 100 * ada)))
        }
        rejects("free cancel signed by the host only") {
            run(Action.Cancel, host, early, scala.Seq(pay(guest, 100 * ada)))
        }
    }

    test("[stated] S2 late Cancel pays the host exactly the fee") {
        accepts("late cancel, host receives 10 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, fee10), pay(guest, 90 * ada)))
        }
        rejects("late cancel, host receives nothing") {
            run(Action.Cancel, guest, late, scala.Seq(pay(guest, 100 * ada)))
        }
        rejects("late cancel, host receives the fee plus 1 lovelace") {
            run(
              Action.Cancel,
              guest,
              late,
              scala.Seq(pay(host, fee10 + 1), pay(guest, 90 * ada - 1))
            )
        }
    }

    test("[stated] S3 Release needs the host's signature") {
        accepts("release signed by the host") {
            run(Action.Release, host, early, scala.Seq(pay(guest, 100 * ada)))
        }
        rejects("release signed by the guest only") {
            run(Action.Release, guest, early, scala.Seq(pay(guest, 100 * ada)))
        }
    }

    test("[stated] S4 Release pays the guest at least the deposit") {
        accepts("guest receives the deposit plus 5 ADA") {
            run(Action.Release, host, early, scala.Seq(pay(guest, 105 * ada)))
        }
        rejects("guest receives the deposit less 1 lovelace") {
            run(
              Action.Release,
              host,
              early,
              scala.Seq(pay(guest, 100 * ada - 1), pay(host, 1))
            )
        }
    }

    test("[stated] S5 Claim needs the host's signature") {
        accepts("claim after the grace period, signed by the host") {
            run(Action.Claim, host, afterGrace, scala.Seq(pay(host, 100 * ada)))
        }
        rejects("claim after the grace period, signed by the guest only") {
            run(Action.Claim, guest, afterGrace, scala.Seq(pay(host, 100 * ada)))
        }
    }

    test("[stated] S6 the fee rounds up") {
        // 25.000001 ADA at 1000 bps is 2500000.1 lovelace. The fee is 2500001.
        val deposit: BigInt = 25_000_001
        accepts("host receives 2500001 lovelace") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 2_500_001)), deposit)
        }
        rejects("host receives 2500000 lovelace") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 2_500_000)), deposit)
        }
    }

    test("[stated] S7 the fee is at least 2 ADA") {
        // 10 ADA at 1000 bps is 1 ADA. The minimum makes it 2 ADA.
        val deposit = 10 * ada
        accepts("host receives 2 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 2 * ada)), deposit)
        }
        rejects("host receives 1 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 1 * ada)), deposit)
        }
    }

    test("[stated] S8 the fee rate is clamped at 5000 bps") {
        // 100 ADA at 9000 bps. The clamp makes the fee 50 ADA.
        val datum = booking(feeBps = 9000)
        accepts("host receives 50 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 50 * ada)), datum = datum)
        }
        rejects("host receives 90 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 90 * ada)), datum = datum)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 2: boundaries
    // ---------------------------------------------------------------------------------------

    test("[boundary] B1 free Cancel ends at cancelBy, inclusive") {
        val noFee = scala.Seq(pay(guest, 100 * ada))
        val withFee = scala.Seq(pay(host, fee10), pay(guest, 90 * ada))
        accepts("upper bound equal to cancelBy, no fee") {
            run(Action.Cancel, guest, endsAt(cancelBy), noFee)
        }
        rejects("upper bound 1 ms after cancelBy, no fee") {
            run(Action.Cancel, guest, endsAt(cancelBy + 1), noFee)
        }
        accepts("upper bound 1 ms after cancelBy, fee paid") {
            run(Action.Cancel, guest, endsAt(cancelBy + 1), withFee)
        }
    }

    test("[boundary] B2 late Cancel ends 1 hour before checkIn, inclusive") {
        val withFee = scala.Seq(pay(host, fee10), pay(guest, 90 * ada))
        accepts("upper bound equal to checkIn less 1 hour") {
            run(Action.Cancel, guest, endsAt(checkIn - lockout), withFee)
        }
        rejects("upper bound 1 ms later") {
            run(Action.Cancel, guest, endsAt(checkIn - lockout + 1), withFee)
        }
    }

    test("[boundary] B3 Claim opens strictly after checkIn plus 6 hours") {
        val toHost = scala.Seq(pay(host, 100 * ada))
        rejects("lower bound equal to checkIn plus 6 hours") {
            run(Action.Claim, host, startsAt(checkIn + grace), toHost)
        }
        accepts("lower bound 1 ms later") {
            run(Action.Claim, host, startsAt(checkIn + grace + 1), toHost)
        }
    }

    test("[boundary] B4 a rate of 5000 bps is not clamped, 5001 is") {
        // 100 ADA: 5000 bps gives 50 ADA. 5001 bps would give 50.01 ADA without the clamp.
        accepts("5000 bps, host receives 50 ADA") {
            run(
              Action.Cancel,
              guest,
              late,
              scala.Seq(pay(host, 50 * ada)),
              datum = booking(feeBps = 5000)
            )
        }
        accepts("5001 bps, host receives 50 ADA") {
            run(
              Action.Cancel,
              guest,
              late,
              scala.Seq(pay(host, 50 * ada)),
              datum = booking(feeBps = 5001)
            )
        }
        rejects("5001 bps, host receives 50.01 ADA") {
            run(
              Action.Cancel,
              guest,
              late,
              scala.Seq(pay(host, 50_010_000)),
              datum = booking(feeBps = 5001)
            )
        }
    }

    test("[boundary] B5 a fee with no remainder is not rounded") {
        // 30 ADA at 1000 bps is exactly 3 ADA.
        val deposit = 30 * ada
        accepts("host receives 3 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 3 * ada)), deposit)
        }
        rejects("host receives 3 ADA plus 1 lovelace") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 3 * ada + 1)), deposit)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 3: cases that follow from the intent
    // ---------------------------------------------------------------------------------------

    test("[intent] I1 Cancel with no upper bound proves no time") {
        val withFee = scala.Seq(pay(host, fee10), pay(guest, 90 * ada))
        accepts("late cancel with an upper bound, fee paid") {
            run(Action.Cancel, guest, late, withFee)
        }
        rejects("cancel with a lower bound only, fee paid") {
            run(Action.Cancel, guest, range(scala.Some(cancelBy - day), scala.None), withFee)
        }
        rejects("cancel with no bounds, no fee") {
            run(
              Action.Cancel,
              guest,
              range(scala.None, scala.None),
              scala.Seq(pay(guest, 100 * ada))
            )
        }
    }

    test("[intent] I2 Claim with no lower bound proves no time") {
        val toHost = scala.Seq(pay(host, 100 * ada))
        accepts("claim with a lower bound after the grace period") {
            run(Action.Claim, host, afterGrace, toHost)
        }
        rejects("claim with an upper bound only") {
            run(
              Action.Claim,
              host,
              range(scala.None, scala.Some(checkIn + grace + day)),
              toHost
            )
        }
    }

    test("[intent] I3 Release needs no clock") {
        accepts("release with no validity bounds") {
            run(
              Action.Release,
              host,
              range(scala.None, scala.None),
              scala.Seq(pay(guest, 100 * ada))
            )
        }
        accepts("release during the grace period") {
            run(
              Action.Release,
              host,
              startsAt(checkIn + hour),
              scala.Seq(pay(guest, 100 * ada))
            )
        }
        rejects("release with no validity bounds, guest receives too little") {
            run(
              Action.Release,
              host,
              range(scala.None, scala.None),
              scala.Seq(pay(guest, 99 * ada), pay(host, ada))
            )
        }
    }

    test("[intent] I4 the upper bound decides the Cancel fee, not the lower bound") {
        // The range starts 2 days before cancelBy and ends 1 hour after it.
        val wide = range(scala.Some(cancelBy - 2 * day), scala.Some(cancelBy + hour))
        rejects("wide range, no fee") {
            run(Action.Cancel, guest, wide, scala.Seq(pay(guest, 100 * ada)))
        }
        accepts("wide range, fee paid") {
            run(Action.Cancel, guest, wide, scala.Seq(pay(host, fee10), pay(guest, 90 * ada)))
        }
    }

    test("[intent] I5 the fee never exceeds the deposit") {
        // 1.5 ADA deposit. The 2 ADA minimum is more than the deposit, so the fee is 1.5 ADA.
        val deposit: BigInt = 1_500_000
        accepts("host receives the whole deposit, 1.5 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, deposit)), deposit)
        }
        rejects("host receives 2 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 2 * ada)), deposit)
        }
    }

    test("[intent] I6 the clamp applies before the minimum fee") {
        // 3 ADA at 9000 bps: the clamp gives 1.5 ADA, then the minimum gives 2 ADA.
        val deposit = 3 * ada
        val datum = booking(feeBps = 9000)
        accepts("host receives 2 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 2 * ada)), deposit, datum)
        }
        rejects("host receives 1.5 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 1_500_000)), deposit, datum)
        }
        rejects("host receives 2.7 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 2_700_000)), deposit, datum)
        }
    }

    test("[intent] I7 a rate of zero still costs the minimum fee") {
        val datum = booking(feeBps = 0)
        accepts("0 bps, host receives 2 ADA") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, 2 * ada)), datum = datum)
        }
        rejects("0 bps, host receives nothing") {
            run(Action.Cancel, guest, late, scala.Seq(pay(guest, 100 * ada)), datum = datum)
        }
    }

    test("[intent] I8 Cancel and Claim do not check where the rest goes") {
        accepts("free cancel, everything goes to a third party") {
            run(Action.Cancel, guest, early, scala.Seq(pay(stranger, 100 * ada)))
        }
        accepts("free cancel, the host receives a gift") {
            run(Action.Cancel, guest, early, scala.Seq(pay(host, 3 * ada), pay(guest, 97 * ada)))
        }
        accepts("late cancel, fee to the host, the rest to a third party") {
            run(Action.Cancel, guest, late, scala.Seq(pay(host, fee10), pay(stranger, 90 * ada)))
        }
        accepts("claim, everything goes to a third party") {
            run(Action.Claim, host, afterGrace, scala.Seq(pay(stranger, 100 * ada)))
        }
        rejects("late cancel, the fee goes to a third party") {
            run(Action.Cancel, guest, late, scala.Seq(pay(stranger, fee10), pay(guest, 90 * ada)))
        }
    }

    test("[intent] I9 one payment cannot settle two deposits") {
        // Two deposits of the same booking parties in one transaction.
        accepts("release of one deposit, guest receives 100 ADA") {
            run(Action.Release, host, early, scala.Seq(pay(guest, 100 * ada)))
        }
        rejects("release with two deposit inputs, guest receives 100 ADA once") {
            run(
              Action.Release,
              host,
              early,
              scala.Seq(pay(guest, 100 * ada), pay(host, 100 * ada)),
              extraDeposits = scala.Seq(100 * ada)
            )
        }
        rejects("late cancel with two deposit inputs, host receives one fee") {
            run(
              Action.Cancel,
              guest,
              late,
              scala.Seq(pay(host, fee10), pay(guest, 190 * ada)),
              extraDeposits = scala.Seq(100 * ada)
            )
        }
    }
}
