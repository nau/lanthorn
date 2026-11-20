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

/** Holds the deposit of one booking. */
@Compile
object Deposit extends Validator {


    /** Sum of lovelace of outputs paid to a pub key credential, ignoring staking part. */
    def sumTo(tx: TxInfo, pkh: PubKeyHash): BigInt =
        tx.outputs.foldLeft(BigInt(0)) { (acc, out) =>
            out.address.credential match
                case Credential.PubKeyCredential(h) =>
                    if h === pkh then acc + out.value.getLovelace else acc
                case _ => acc
        }

    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit = {
        // spec [GEN-2]
        val booking = datum.getOrFail("missing datum").to[Booking]
        // spec [GEN-1] undecodable redeemer / datum fails here
        val action = redeemer.to[Action]
        val CancelLeadMs = BigInt(3_600_000) // cancel cutoff = checkIn - 1h
        val GraceMs = BigInt(21_600_000) // grace period, 6h
        val MaxFeeBps = BigInt(5000)
        val MinFee = BigInt(2_000_000)
        val ownInput = tx.findInputOrFail(ownRef, "own input not found")
        // spec [GEN-4] deposit is the lovelace of the input named by ownRef
        val deposit = ownInput.resolved.value.getLovelace // spec [GEN-5]
        // spec [GEN-3] exactly one deposit input
        val ownCred = ownInput.resolved.address.credential
        require(
          tx.inputs.count(_.resolved.address.credential === ownCred) === BigInt(1),
          "exactly one deposit input"
        )
        action match
            case Action.Cancel =>
                // spec [CAN-1]
                require(tx.isSignedBy(booking.guest), "guest signature required")
                // spec [CAN-2] [GEN-7] upper bound must be present
                val u = tx.validRange.to.finiteOrFail("upper bound required")
                // spec [CAN-3] free window tested first
                if u <= booking.cancelBy then () // spec [CAN-4] free: no fee, no outputs
                else
                    // spec [CAN-5]
                    require(u <= booking.checkIn - CancelLeadMs, "cancel cutoff passed")
                    // spec [CAN-6]
                    val rate =
                        if booking.feeBps > MaxFeeBps then MaxFeeBps else booking.feeBps
                    // spec [CAN-7]
                    val rounded = (deposit * rate).divCeil(BigInt(10_000))
                    // spec [CAN-8]
                    val floored = if rounded < MinFee then MinFee else rounded
                    // spec [CAN-9] cap is the last step
                    val fee = if floored > deposit then deposit else floored
                    // spec [CAN-10] exact; [CAN-11] guest outputs unchecked
                    require(sumTo(tx, booking.host) === fee, "host sum must equal fee")
            case Action.Release =>
                // spec [REL-1]
                require(tx.isSignedBy(booking.host), "host signature required")
                // spec [REL-2] no time check; [REL-4] no other output limit
                // spec [REL-3]
                require(sumTo(tx, booking.guest) >= deposit, "guest must receive deposit")
            case Action.Claim =>
                // spec [CLM-1]
                require(tx.isSignedBy(booking.host), "host signature required")
                // spec [CLM-2] [GEN-7] strict; [CLM-3] upper bound unused; [CLM-4] no output check
                val l = tx.validRange.from.finiteOrFail("lower bound required")
                require(l > booking.checkIn + GraceMs, "grace period not over")
    }
}
