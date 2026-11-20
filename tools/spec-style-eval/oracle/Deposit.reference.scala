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

/** Reference implementation of the oracle. It follows `notes.md`, decision by decision. The
  * `D-n` tags are the rows of `decisions.md`.
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
        val ownInput = tx.findInputOrFail(ownRef)
        val ownCredential = ownInput.resolved.address.credential
        // D-17: one deposit input per transaction, for every action.
        tx.inputs.findUniqueOrFail(
          _.resolved.address.credential === ownCredential,
          "Exactly one deposit input per transaction"
        )
        // D-1: the deposit is the lovelace of the spent UTxO.
        val deposit = ownInput.resolved.value.getLovelace

        action match
            case Action.Cancel =>
                // D-2
                require(tx.isSignedBy(booking.guest), "Guest must sign Cancel")
                // D-3
                val upper = tx.validToOrFail("Cancel needs an upper validity bound")
                // D-4: free up to and including cancelBy. No output check (D-11).
                if upper <= booking.cancelBy then ()
                else
                    // D-5: late cancel closes one hour before check-in.
                    require(
                      upper <= booking.checkIn - CancelLockout,
                      "Too late to cancel"
                    )
                    // D-10: the host receives exactly the fee.
                    require(
                      paidTo(tx, booking.host) === lateFee(deposit, booking.feeBps),
                      "Host must receive exactly the fee"
                    )
            case Action.Release =>
                // D-12, D-13: host signs, no time check.
                require(tx.isSignedBy(booking.host), "Host must sign Release")
                // D-14
                require(
                  paidTo(tx, booking.guest) >= deposit,
                  "Guest must receive at least the deposit"
                )
            case Action.Claim =>
                // D-15
                require(tx.isSignedBy(booking.host), "Host must sign Claim")
                // D-16: strictly after check-in plus the grace period.
                val lower = tx.validFromOrFail("Claim needs a lower validity bound")
                require(lower > booking.checkIn + Grace, "Too early to claim")
    }

    /** D-6 to D-9, in this order: clamp the rate, round up, apply the minimum, cap at the
      * deposit.
      */
    def lateFee(deposit: BigInt, feeBps: BigInt): BigInt = {
        val bps = if feeBps > MaxFeeBps then MaxFeeBps else feeBps
        val raw = (deposit * bps) divCeil BigInt(10000)
        val withMinimum = if raw < MinFee then MinFee else raw
        if withMinimum > deposit then deposit else withMinimum
    }

    /** Lovelace over all outputs to the payment key `pkh`. The staking part does not matter. */
    def paidTo(tx: TxInfo, pkh: PubKeyHash): BigInt =
        tx.findOutputsByCredential(Credential.PubKeyCredential(pkh))
            .foldLeft(BigInt(0))((sum, out) => sum + out.value.getLovelace)

    val MaxFeeBps: BigInt = 5000
    val MinFee: BigInt = 2_000_000
    val CancelLockout: BigInt = 3_600_000
    val Grace: BigInt = 21_600_000
}
