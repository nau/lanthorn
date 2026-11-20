package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.Data.toData
import scalus.cardano.ledger.*
import scalus.cardano.node.Emulator
import scalus.cardano.txbuilder.RedeemerPurpose.ForSpend
import scalus.cardano.txbuilder.{TxBuilder, txBuilder}
import scalus.cardano.onchain.plutus.v1.PubKeyHash
import scalus.cardano.onchain.plutus.v3.ScriptInfo
import scalus.testing.kit.Party.{Alice, Bob, Charles}
import scalus.testing.kit.{ScalusTest, TestUtil}
import scalus.testing.kit.TestUtil.getScriptContextV3
import scalus.uplc.eval.Result
import scalus.utils.await

import java.time.Instant

class DepositTest extends AnyFunSuite, ScalusTest {

    private given Options = Options.default
    private given env: CardanoInfo = TestUtil.testEnvironment

    private val contract = PlutusV3.compile(Deposit.validate)
    private val scriptAddress = contract.address(env.network)

    private val cancelBy = 1_700_000_000_000L
    private val checkIn = 1_700_100_000_000L
    private val cutoff = checkIn - 3_600_000L
    private val graceEnd = checkIn + 21_600_000L
    private val ada = 1_000_000L

    private def booking(
        feeBps: Long = 1000,
        cancelBy: Long = cancelBy,
        checkIn: Long = checkIn,
        host: Party = Bob
    ) = Booking(
      PubKeyHash(Alice.addrKeyHash),
      PubKeyHash(host.addrKeyHash),
      BigInt(cancelBy),
      BigInt(checkIn),
      BigInt(feeBps)
    )

    private type Party = scalus.testing.kit.Party

    /** Locks the given deposits (booking, lovelace) in one tx. */
    private def lock(deposits: Seq[(Booking, Long)]): (Seq[Utxo], Emulator) = {
        val provider = Emulator.withAddresses(Seq(Alice.address))
        val funding = provider.findUtxos(Alice.address).await().toOption.get
        val tx = deposits
            .foldLeft(TxBuilder(env)) { case (b, (bk, v)) =>
                b.payTo(scriptAddress, Value.lovelace(v), bk)
            }
            .complete(availableUtxos = funding, sponsor = Alice.address)
            .sign(Alice.signer)
            .transaction
        assert(provider.submit(tx).await().isRight)
        val us = tx.utxos.collect {
            case kv @ (_, out) if out.address == scriptAddress => Utxo(kv)
        }.toSeq
        // keep order by lovelace of request
        val sorted = deposits.map { case (_, v) =>
            us.find(_.output.value.coin.value == v).get
        }
        (sorted, provider)
    }

    /** Evaluates the validator for spending `deposits(0)` with `action`. */
    private def run(
        action: Action,
        bk: Booking = booking(),
        deposit: Long = 25 * ada,
        signer: Party = Alice,
        from: Option[Long] = None,
        to: Option[Long] = None,
        outputs: Seq[(scalus.cardano.address.Address, Long)] = Seq.empty,
        extraDeposits: Seq[(Booking, Long)] = Seq.empty,
        noSigner: Boolean = false
    ): Boolean = {
        val (us, provider) = lock((bk, deposit) +: extraDeposits)
        val b0 = us.foldLeft(txBuilder) { (b, u) =>
            b.spend(u, action, contract)
        }
        val b1 =
            if noSigner then b0 else b0.requireSignature(signer.addrKeyHash)
        val b2 = from.fold(b1)(f => b1.validFrom(Instant.ofEpochMilli(f)))
        val b3 = to.fold(b2)(t => b2.validTo(Instant.ofEpochMilli(t)))
        val b4 = outputs.foldLeft(b3) { case (b, (a, v)) =>
            b.payTo(a, Value.lovelace(v))
        }
        val ctx =
            b4.draft.getScriptContextV3(provider.utxos, ForSpend(us.head.input))
        contract.program.runWithDebug(ctx).isSuccess
    }

    private def pay(p: Party, v: Long) =
        (p.address: scalus.cardano.address.Address, v)

    // ---------- GEN ----------

    test("GEN-2 missing datum rejects") {
        // spec [GEN-2]
        val (us, provider) = lock(Seq((booking(), 25 * ada)))
        val ctx = txBuilder
            .spend(us.head, Action.Release, contract)
            .requireSignature(Bob.addrKeyHash)
            .payTo(Alice.address, Value.lovelace(25 * ada))
            .draft
            .getScriptContextV3(provider.utxos, ForSpend(us.head.input))
        assert(contract.program.runWithDebug(ctx).isSuccess)
        val noDatum = ctx.scriptInfo match
            case ScriptInfo.SpendingScript(ref, _) =>
                ctx.copy(scriptInfo =
                    ScriptInfo.SpendingScript(ref, scalus.cardano.onchain.plutus.prelude.Option.None)
                )
            case _ => fail("unexpected script info")
        assert(contract.program.runWithDebug(noDatum).isFailure)
    }

    test("GEN-3 two deposit inputs reject") {
        // spec [GEN-3]
        assert(
          !run(
            Action.Release,
            signer = Bob,
            outputs = Seq(pay(Alice, 100 * ada)),
            extraDeposits = Seq((booking(), 26 * ada))
          )
        )
        assert(
          run(Action.Release, signer = Bob, outputs = Seq(pay(Alice, 25 * ada)))
        )
    }

    test("GEN-8 same guest and host key") {
        // spec [GEN-8]
        val same = booking(host = Alice)
        assert(!run(Action.Claim, same, from = Some(graceEnd)))
        assert(run(Action.Claim, same, from = Some(graceEnd + 1000)))
    }

    // ---------- Cancel ----------

    test("CAN-1 guest must sign") {
        // spec [CAN-1]
        assert(!run(Action.Cancel, signer = Bob, to = Some(cancelBy)))
        assert(!run(Action.Cancel, noSigner = true, to = Some(cancelBy)))
        assert(run(Action.Cancel, to = Some(cancelBy)))
    }

    test("CAN-2 no upper bound rejects") {
        // spec [CAN-2] [GEN-7]
        assert(!run(Action.Cancel))
        assert(!run(Action.Cancel, from = Some(cancelBy - 5000)))
    }

    test("CAN-3 CAN-4 free cancellation, no output check, bad datum ok") {
        // spec [CAN-3] [CAN-4]
        assert(run(Action.Cancel, to = Some(cancelBy)))
        assert(run(Action.Cancel, booking(feeBps = -5), to = Some(cancelBy)))
        // cancelBy later than cutoff: free window still wins
        val late = booking(cancelBy = cutoff + 2_000_000)
        assert(run(Action.Cancel, late, to = Some(cutoff + 2_000_000)))
        assert(!run(Action.Cancel, late, to = Some(cutoff + 2_001_000)))
    }

    test("CAN-5 cutoff") {
        // spec [CAN-5] [CAN-10]
        val fee = 2_500_000L // 10% of 25 ADA
        assert(
          run(Action.Cancel, to = Some(cutoff), outputs = Seq(pay(Bob, fee)))
        )
        assert(
          !run(
            Action.Cancel,
            to = Some(cutoff + 1000),
            outputs = Seq(pay(Bob, fee))
          )
        )
        assert(
          run(
            Action.Cancel,
            to = Some(cancelBy + 1000),
            outputs = Seq(pay(Bob, fee))
          )
        )
    }

    test("CAN-6 rate capped at 5000") {
        // spec [CAN-6]
        val t = Some(cutoff)
        assert(
          run(
            Action.Cancel,
            booking(9000),
            to = t,
            outputs = Seq(pay(Bob, 12_500_000))
          )
        )
        assert(
          !run(
            Action.Cancel,
            booking(9000),
            to = t,
            outputs = Seq(pay(Bob, 22_500_000))
          )
        )
    }

    test("CAN-7 ceiling") {
        // spec [CAN-7]
        val t = Some(cutoff)
        assert(
          run(
            Action.Cancel,
            booking(1000),
            25_000_001,
            to = t,
            outputs = Seq(pay(Bob, 2_500_001))
          )
        )
        assert(
          !run(
            Action.Cancel,
            booking(1000),
            25_000_001,
            to = t,
            outputs = Seq(pay(Bob, 2_500_000))
          )
        )
    }

    test("CAN-8 minimum fee") {
        // spec [CAN-8]
        val t = Some(cutoff)
        assert(
          run(
            Action.Cancel,
            booking(0),
            10 * ada,
            to = t,
            outputs = Seq(pay(Bob, 2_000_000))
          )
        )
        assert(
          !run(
            Action.Cancel,
            booking(0),
            10 * ada,
            to = t,
            outputs = Seq(pay(Bob, 0))
          )
        )
        // negative feeBps also gives the minimum
        assert(
          run(
            Action.Cancel,
            booking(-100),
            10 * ada,
            to = t,
            outputs = Seq(pay(Bob, 2_000_000))
          )
        )
    }

    test("CAN-9 fee capped at deposit") {
        // spec [CAN-9]
        val t = Some(cutoff)
        assert(
          run(
            Action.Cancel,
            booking(0),
            1_500_000,
            to = t,
            outputs = Seq(pay(Bob, 1_500_000))
          )
        )
        assert(
          !run(
            Action.Cancel,
            booking(0),
            1_500_000,
            to = t,
            outputs = Seq(pay(Bob, 2_000_000))
          )
        )
    }

    test("CAN-10 host sum exact, summed over outputs, stake ignored") {
        // spec [CAN-10]
        val t = Some(cutoff)
        val fee = 2_500_000L
        assert(!run(Action.Cancel, to = t, outputs = Seq(pay(Bob, fee + 1))))
        assert(!run(Action.Cancel, to = t))
        assert(
          run(
            Action.Cancel,
            to = t,
            outputs = Seq(pay(Bob, 1_000_000), pay(Bob, 1_500_000))
          )
        )
    }

    test("CAN-11 guest outputs not checked") {
        // spec [CAN-11]
        assert(
          run(
            Action.Cancel,
            to = Some(cutoff),
            outputs = Seq(pay(Bob, 2_500_000))
          )
        )
        assert(
          run(
            Action.Cancel,
            to = Some(cutoff),
            outputs = Seq(pay(Bob, 2_500_000), pay(Alice, 5 * ada))
          )
        )
    }

    // ---------- Release ----------

    test("REL-1 host must sign") {
        // spec [REL-1]
        assert(
          !run(
            Action.Release,
            signer = Alice,
            outputs = Seq(pay(Alice, 25 * ada))
          )
        )
        assert(
          !run(
            Action.Release,
            signer = Charles,
            outputs = Seq(pay(Alice, 25 * ada))
          )
        )
        assert(
          run(Action.Release, signer = Bob, outputs = Seq(pay(Alice, 25 * ada)))
        )
    }

    test("REL-2 no time check") {
        // spec [REL-2]
        val o = Seq(pay(Alice, 25 * ada))
        assert(run(Action.Release, signer = Bob, outputs = o, to = Some(1000)))
        assert(
          run(
            Action.Release,
            signer = Bob,
            outputs = o,
            from = Some(graceEnd * 2)
          )
        )
    }

    test("REL-3 guest sum at least deposit") {
        // spec [REL-3] [REL-4]
        assert(
          run(
            Action.Release,
            deposit = 25_000_001,
            signer = Bob,
            outputs = Seq(pay(Alice, 25_000_001))
          )
        )
        assert(
          !run(
            Action.Release,
            deposit = 25_000_001,
            signer = Bob,
            outputs = Seq(pay(Alice, 25_000_000))
          )
        )
        assert(
          run(
            Action.Release,
            deposit = 25_000_001,
            signer = Bob,
            outputs = Seq(pay(Alice, 20_000_000), pay(Alice, 5_000_001))
          )
        )
        assert(
          run(
            Action.Release,
            signer = Bob,
            outputs = Seq(pay(Alice, 30 * ada), pay(Bob, 3 * ada))
          )
        )
        assert(
          !run(Action.Release, signer = Bob, outputs = Seq(pay(Bob, 25 * ada)))
        )
    }

    // ---------- Claim ----------

    test("CLM-1 host must sign") {
        // spec [CLM-1]
        assert(!run(Action.Claim, signer = Alice, from = Some(graceEnd + 1000)))
        assert(run(Action.Claim, signer = Bob, from = Some(graceEnd + 1000)))
    }

    test("CLM-2 strict grace bound; GEN-7 absent lower bound") {
        // spec [CLM-2] [GEN-7]
        assert(!run(Action.Claim, signer = Bob))
        assert(
          !run(Action.Claim, signer = Bob, to = Some(graceEnd + 10_000_000))
        )
        assert(!run(Action.Claim, signer = Bob, from = Some(graceEnd)))
        assert(run(Action.Claim, signer = Bob, from = Some(graceEnd + 1000)))
    }

    test("CLM-3 CLM-4 upper bound and outputs unused") {
        // spec [CLM-3] [CLM-4]
        assert(
          run(
            Action.Claim,
            signer = Bob,
            from = Some(graceEnd + 1000),
            to = Some(graceEnd + 100_000_000),
            outputs = Seq(pay(Charles, 25 * ada))
          )
        )
    }
}
