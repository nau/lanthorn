package validator

import scalus.compiler.Compile
import scalus.uplc.builtin.Data
import scalus.cardano.onchain.plutus.v3.*
import scalus.cardano.onchain.plutus.prelude.*

/** Datum. Locked with the deposit when the guest books. */
case class Booking(
    guest: PubKeyHash,
    host: PubKeyHash,
    cancelBy: PosixTime,
    checkIn: PosixTime,
    feeBps: BigInt
) derives FromData,
      ToData

/** Redeemer. */
enum Action derives FromData, ToData:
    case Cancel
    case Release
    case Claim

/** A validator written "from memory", in the way stock examples do it. The oracle check runs
  * the hidden tests against it. It MUST fail many of them. If it does not, the task measures
  * what a model remembers, not what the spec says.
  *
  * Stock choices: the fee rounds down, the host receives at least the fee, no minimum fee, no
  * clamp, no cap, late Cancel runs up to checkIn, Claim opens at checkIn, and nothing limits
  * the number of deposit inputs.
  */
@Compile
object Deposit extends Validator {
    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit = {
        val booking = datum.getOrFail("Datum not found").to[Booking]
        val action = redeemer.to[Action]
        val deposit = tx.findInputOrFail(ownRef).resolved.value.getLovelace

        action match
            case Action.Cancel =>
                require(tx.isSignedBy(booking.guest), "Guest must sign Cancel")
                val upper = tx.validToOrFail("Cancel needs an upper validity bound")
                if upper <= booking.cancelBy then ()
                else
                    require(upper <= booking.checkIn, "Too late to cancel")
                    val fee = (deposit * booking.feeBps) divFloor BigInt(10000)
                    require(paidTo(tx, booking.host) >= fee, "Host must receive the fee")
            case Action.Release =>
                require(tx.isSignedBy(booking.host), "Host must sign Release")
                require(
                  paidTo(tx, booking.guest) >= deposit,
                  "Guest must receive at least the deposit"
                )
            case Action.Claim =>
                require(tx.isSignedBy(booking.host), "Host must sign Claim")
                val lower = tx.validFromOrFail("Claim needs a lower validity bound")
                require(lower >= booking.checkIn, "Too early to claim")
    }

    def paidTo(tx: TxInfo, pkh: PubKeyHash): BigInt =
        tx.findOutputsByCredential(Credential.PubKeyCredential(pkh))
            .foldLeft(BigInt(0))((sum, out) => sum + out.value.getLovelace)
}
