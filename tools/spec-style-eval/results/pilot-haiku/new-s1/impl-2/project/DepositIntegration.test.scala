package validator

import org.scalatest.funsuite.AnyFunSuite
import scalus.compiler.Options
import scalus.uplc.PlutusV3
import scalus.uplc.builtin.Data.toData
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

/** Locks a booking's deposit at the contract and spends it through the selected
  * backend.
  *
  * Backend is chosen by the SCALUS_TEST_ENV env var (or -Dscalus.testEnv):
  * "emulator" (default, in-memory) or "yaci" (a local Yaci DevKit node,
  * requires Docker).
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

    private val checkIn: BigInt = 1_100_000_000
    private val cancelBy: BigInt = 1_000_000_000

    /** Locks 10 ADA at the contract for a booking of `guest` at `host`. Returns
      * the locked UTxO.
      */
    private def lock(
        ctx: IntegrationTestContext,
        guest: TestParty,
        host: TestParty
    ): Utxo = {
        val scriptAddress = contract.address(ctx.cardanoInfo.network)
        val booking = Booking(
          guest = PubKeyHash(guest.addrKeyHash),
          host = PubKeyHash(host.addrKeyHash),
          cancelBy = cancelBy,
          checkIn = checkIn,
          feeBps = BigInt(1000)
        )

        val funding = ctx.provider.findUtxos(guest.address).await().toOption.get
        val lockTx = TxBuilder(ctx.cardanoInfo)
            .payTo(scriptAddress, Value.ada(10), booking)
            .complete(availableUtxos = funding, sponsor = guest.address)
            .sign(guest.signer)
            .transaction
        assert(ctx.submit(lockTx).await().isRight)

        Utxo(
          ctx.provider.findUtxos(scriptAddress).await().toOption.get.head
        )
    }

    /** Spends the locked deposit as `spender`, releasing 10 ADA to `guest`.
      * Returns whether the node accepted the spend.
      */
    private def releaseAs(
        ctx: IntegrationTestContext,
        locked: Utxo,
        guest: TestParty,
        spender: TestParty
    ): Boolean = {
        val spenderUtxos =
            ctx.provider.findUtxos(spender.address).await().toOption.get

        Try {
            val spendTx = TxBuilder(ctx.cardanoInfo)
                .spend(locked, Action.Release, contract)
                .requireSignature(spender.addrKeyHash)
                .payTo(guest.address, Value.ada(10))
                .complete(
                  availableUtxos = spenderUtxos,
                  sponsor = spender.address
                )
                .sign(spender.signer)
                .transaction
            ctx.submit(spendTx).await()
        }.toOption.exists(_.isRight)
    }

    // spec [RE-1] [RE-2]
    test(s"[$testEnvName] host releases the deposit to the guest") {
        val ctx = createTestContext()
        val locked = lock(ctx, guest = ctx.alice, host = ctx.bob)
        assert(releaseAs(ctx, locked, guest = ctx.alice, spender = ctx.bob))
    }

    // spec [RE-1]
    test(s"[$testEnvName] guest cannot release to themselves") {
        val ctx = createTestContext()
        val locked = lock(ctx, guest = ctx.alice, host = ctx.bob)
        assert(!releaseAs(ctx, locked, guest = ctx.alice, spender = ctx.alice))
    }
}
