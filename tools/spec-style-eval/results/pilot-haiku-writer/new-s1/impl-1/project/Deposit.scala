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
    val hour: BigInt = 3_600_000
    val grace: BigInt = 21_600_000
    val maxBps: BigInt = 5000
    val minFee: BigInt = 2_000_000

    /** Sum of lovelace paid to public key payment credential `pkh`. */
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
        // spec [GEN-2] missing datum rejects; undecodable datum fails too [GEN-1]
        val b = datum.getOrFail("missing datum").to[Booking]
        val action = redeemer.to[Action]
        // spec [GEN-4] deposit is the lovelace of the input ownRef names
        val own = tx.inputs.findUniqueOrFail(_.outRef === ownRef, "own input")
        val deposit = own.resolved.value.getLovelace
        // spec [GEN-3] exactly one deposit input
        val cred = own.resolved.address.credential
        tx.inputs.findUniqueOrFail(
          _.resolved.address.credential === cred,
          "exactly one deposit input"
        )
        action match
            case Action.Cancel =>
                // spec [CAN-1]
                require(tx.isSignedBy(b.guest), "guest must sign")
                // spec [CAN-2], [GEN-7]
                val u = tx.validToOrFail("upper bound required")
                // spec [CAN-3] free window first; [CAN-4] no fee, no output check
                if u <= b.cancelBy then ()
                else
                    // spec [CAN-5]
                    require(u <= b.checkIn - hour, "cancel cutoff passed")
                    // spec [CAN-6]
                    val rate = if b.feeBps > maxBps then maxBps else b.feeBps
                    // spec [CAN-7]
                    val raw = (deposit * rate) divCeil BigInt(10_000)
                    // spec [CAN-8]
                    val floored = if raw < minFee then minFee else raw
                    // spec [CAN-9]
                    val fee = if floored > deposit then deposit else floored
                    // spec [CAN-10] exact; [CAN-11] guest outputs unchecked
                    require(
                      sumTo(tx, b.host) === fee,
                      "host must get exact fee"
                    )
            case Action.Release =>
                // spec [REL-1]
                require(tx.isSignedBy(b.host), "host must sign")
                // spec [REL-2] no time check; [REL-4] no other output limit
                // spec [REL-3]
                require(sumTo(tx, b.guest) >= deposit, "guest must get deposit")
            case Action.Claim =>
                // spec [CLM-1]
                require(tx.isSignedBy(b.host), "host must sign")
                // spec [CLM-2], [GEN-7]; [CLM-3] U unused; [CLM-4] outputs unchecked
                val l = tx.validFromOrFail("lower bound required")
                require(l > b.checkIn + grace, "grace period not over")
    }
}
