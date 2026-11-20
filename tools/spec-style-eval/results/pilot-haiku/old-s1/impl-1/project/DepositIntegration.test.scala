package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.cardano.ledger.*
import scalus.cardano.txbuilder.TxBuilder
import scalus.cardano.onchain.plutus.v1.PubKeyHash
import scalus.testing.integration.{
    IntegrationTest,
    IntegrationTestContext,
    TestParty
}
import scalus.utils.await

import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Try

/** Locks a `Booking` at the contract and submits a Release to the selected backend.
  *
  * Backend is chosen by the SCALUS_TEST_ENV env var (or -Dscalus.testEnv):
  * "emulator" (default, in-memory) or "yaci" (a local Yaci DevKit node, requires Docker).
  *
  * Alice is both guest and host, which the spec allows. Only the decoded datum and the redeemer
  * matter to the validator.
  */
class DepositIntegrationTest extends AnyFunSuite, IntegrationTest {

    override protected lazy val testEnvName: String =
        sys.env
            .get("SCALUS_TEST_ENV")
            .orElse(sys.props.get("scalus.testEnv"))
            .getOrElse("emulator")
            .toLowerCase
            .trim

    private given Options = Options.release
    private val contract = PlutusV3.compile(Deposit.validate)

    /** Locks 10 ada for `party` as guest and host, then submits a Release that pays `paid` ada to
      * the guest. Returns whether the node accepted the spend.
      */
    private def lockThenRelease(
        ctx: IntegrationTestContext,
        party: TestParty,
        paid: Long
    ): Boolean = {
        val scriptAddress = contract.address(ctx.cardanoInfo.network)
        val pkh = PubKeyHash(party.addrKeyHash)
        val datum = Booking(
          guest = pkh,
          host = pkh,
          cancelBy = BigInt(0),
          checkIn = BigInt(0),
          feeBps = BigInt(1000)
        )

        val funding = ctx.provider.findUtxos(party.address).await().toOption.get
        val lockTx = TxBuilder(ctx.cardanoInfo)
            .payTo(scriptAddress, Value.ada(10), datum)
            .complete(availableUtxos = funding, sponsor = party.address)
            .sign(party.signer)
            .transaction
        assert(ctx.submit(lockTx).await().isRight)

        val lockedUtxo =
            Utxo(
              ctx.provider.findUtxos(scriptAddress).await().toOption.get.head
            )
        val spenderUtxos =
            ctx.provider.findUtxos(party.address).await().toOption.get

        Try {
            val spendTx = TxBuilder(ctx.cardanoInfo)
                .spend(lockedUtxo, Action.Release, contract)
                .requireSignature(party.addrKeyHash)
                .payTo(party.address, Value.ada(paid))
                .complete(
                  availableUtxos = spenderUtxos,
                  sponsor = party.address
                )
                .sign(party.signer)
                .transaction
            ctx.submit(spendTx).await()
        }.toOption.exists(_.isRight)
    }

    // spec [REL-1] [REL-2] [REL-4]
    test(s"[$testEnvName] Release signed by the host, paying the full deposit, is accepted") {
        val ctx = createTestContext()
        assert(lockThenRelease(ctx, party = ctx.alice, paid = 10))
    }

    // spec [REL-2]
    test(s"[$testEnvName] Release paying the guest less than the deposit is rejected") {
        val ctx = createTestContext()
        assert(!lockThenRelease(ctx, party = ctx.alice, paid = 9))
    }
}
