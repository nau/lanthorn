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
    val NoticePeriod: BigInt = 3_600_000
    val GracePeriod: BigInt = 21_600_000
    val MinFee: BigInt = 2_000_000
    val MaxBps: BigInt = 5000

    /** Sum of lovelace over outputs paid to the payment key hash `pkh`. */
    def paidTo(tx: TxInfo, pkh: PubKeyHash): BigInt =
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
        // spec [SP-1]
        val d = datum.getOrFail("no datum")
        // spec [SP-2]
        val booking = d.to[Booking]
        // spec [SP-3]
        val action = redeemer.to[Action]
        // spec [SP-4]
        val own = tx.inputs.find(_.outRef === ownRef).getOrFail("own input not found")
        val ownCred = own.resolved.address.credential
        // spec [SP-5] [SP-6]: same rule for every action
        val n = tx.inputs.count(_.resolved.address.credential === ownCred)
        require(n === BigInt(1), "exactly one deposit input")
        // spec [SP-7]
        val deposit = own.resolved.value.getLovelace
        val signed = (pkh: PubKeyHash) => tx.signatories.contains(pkh)

        action match
            case Action.Cancel =>
                // spec [CA-1]
                require(signed(booking.guest), "guest must sign")
                // spec [CA-2] [CA-3]
                val u = tx.validRange.to.finiteOrFail("upper bound infinite")
                // spec [CA-4] [CA-5] [CA-15]
                if u <= booking.cancelBy then ()
                else
                    // spec [CA-6]
                    require(u <= booking.checkIn - NoticePeriod, "too late to cancel")
                    // spec [CA-7] [CA-8] [CA-12]
                    val rate =
                        if booking.feeBps < 0 then BigInt(0)
                        else if booking.feeBps > MaxBps then MaxBps
                        else booking.feeBps
                    // spec [CA-9]
                    val raw = (deposit * rate).divCeil(10000)
                    // spec [CA-10]
                    val atLeast = if raw < MinFee then MinFee else raw
                    // spec [CA-11]
                    val fee = if atLeast > deposit then deposit else atLeast
                    // spec [CA-13] [CA-14]
                    require(paidTo(tx, booking.host) === fee, "host must receive exact fee")
            case Action.Release =>
                // spec [RE-1]
                require(signed(booking.host), "host must sign")
                // spec [RE-2] [RE-3] [RE-4]
                require(paidTo(tx, booking.guest) >= deposit, "guest must receive deposit")
            case Action.Claim =>
                // spec [CL-1]
                require(signed(booking.host), "host must sign")
                // spec [CL-2]
                val l = tx.validRange.from.finiteOrFail("lower bound infinite")
                // spec [CL-3] [CL-4] [CL-5]
                require(l > booking.checkIn + GracePeriod, "grace period not over")
    }
}
