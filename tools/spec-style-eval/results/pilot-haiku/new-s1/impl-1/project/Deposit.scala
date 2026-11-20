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

/** Holds the deposit of one booking. Specification: SPEC.md. */
@Compile
object Deposit extends Validator {

    // Constants from SPEC.md, "Definitions". All times are POSIX milliseconds.
    inline def NoticePeriod: BigInt = 3_600_000
    inline def GracePeriod: BigInt = 21_600_000
    inline def MinimumFee: BigInt = 2_000_000
    inline def MaximumBps: BigInt = 5000
    inline def BasisPointsDenominator: BigInt = 10000

    // Error messages
    inline val NoDatum = "No datum"
    inline val NoOwnInput = "Own input not found"
    inline val MultipleDepositInputs = "Must spend exactly one deposit"
    inline val UnsignedByGuest = "Must be signed by the guest"
    inline val UnsignedByHost = "Must be signed by the host"
    inline val NoUpperBound = "Validity range must have an upper bound"
    inline val NoLowerBound = "Validity range must have a lower bound"
    inline val TooLateToCancel = "Cancel is closed after the notice period"
    inline val WrongHostPayment = "Host must receive exactly the fee"
    inline val GuestUnderpaid = "Guest must receive at least the deposit"
    inline val TooEarlyToClaim = "Claim is only allowed after the grace period"

    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit = {
        // spec [SP-1] [SP-2]: the datum must be present and decode as Booking
        val booking = datum.getOrFail(NoDatum).to[Booking]
        // spec [SP-3]: the redeemer must decode as Action
        val action = redeemer.to[Action]
        // spec [SP-4]: ownRef must name an input of this transaction
        val ownInput = tx.findInputOrFail(ownRef, NoOwnInput)
        // spec [SP-5] [SP-6]: exactly one input at the deposit script, for every action
        val ownCredential = ownInput.resolved.address.credential
        tx.inputs.findUniqueOrFail(
          _.resolved.address.credential === ownCredential,
          MultipleDepositInputs
        )
        // spec [SP-7]: the deposit is the lovelace of the own input only
        val deposit = Value.getLovelace(ownInput.resolved.value)
        // spec [SP-8] [SP-9]: run exactly the checks of the named action
        action match
            case Action.Cancel  => cancel(booking, deposit, tx)
            case Action.Release => release(booking, deposit, tx)
            case Action.Claim   => claim(booking, tx)
    }

    /** Guest cancels. Free up to cancelBy; a late cancel before checkIn - notice pays the fee to
      * the host.
      */
    def cancel(booking: Booking, deposit: BigInt, tx: TxInfo): Unit = {
        // spec [CA-1]: signed by the guest
        require(tx.isSignedBy(booking.guest), UnsignedByGuest)
        // spec [CA-2] [CA-3]: U must be finite; L is not read
        val upper = tx.validToOrFail(NoUpperBound)
        // spec [CA-4] [CA-5] [CA-15]: free cancel, tested before the late cancel, no output check
        if upper <= booking.cancelBy then ()
        else {
            // spec [CA-6]: otherwise the cancel must be before checkIn - notice period
            require(upper <= booking.checkIn - NoticePeriod, TooLateToCancel)
            // spec [CA-13] [CA-14]: host output sum must equal the fee; guest outputs unchecked
            val fee = lateFee(booking.feeBps, deposit)
            require(sumPaidTo(tx.outputs, booking.host) === fee, WrongHostPayment)
        }
    }

    /** Fee on a late cancel, computed as in spec [CA-7] to [CA-11], in that order. */
    def lateFee(feeBps: BigInt, deposit: BigInt): BigInt = {
        // spec [CA-7]: rate = min(feeBps, maximum bps)
        val capped = if feeBps > MaximumBps then MaximumBps else feeBps
        // spec [CA-8]: a negative rate counts as 0
        val rate = if capped < 0 then BigInt(0) else capped
        // spec [CA-9]: raw fee = ceil(deposit * rate / 10000), rounding in the host's favour
        val raw = Math.divCeil(deposit * rate, BasisPointsDenominator)
        // spec [CA-10]: raise to the minimum fee
        val raised = if raw < MinimumFee then MinimumFee else raw
        // spec [CA-11]: never more than the deposit
        if raised > deposit then deposit else raised
    }

    /** Host releases the deposit to the guest. No clock applies. */
    def release(booking: Booking, deposit: BigInt, tx: TxInfo): Unit = {
        // spec [RE-1]: signed by the host
        require(tx.isSignedBy(booking.host), UnsignedByHost)
        // spec [RE-2] [RE-3] [RE-4]: guest output sum at least the deposit; no range check, no equality
        require(sumPaidTo(tx.outputs, booking.guest) >= deposit, GuestUnderpaid)
    }

    /** Host keeps the deposit after a no-show. */
    def claim(booking: Booking, tx: TxInfo): Unit = {
        // spec [CL-1]: signed by the host
        require(tx.isSignedBy(booking.host), UnsignedByHost)
        // spec [CL-2] [CL-4]: L must be finite; U is not read
        val lower = tx.validFromOrFail(NoLowerBound)
        // spec [CL-3]: strictly after checkIn + grace period
        require(lower > booking.checkIn + GracePeriod, TooEarlyToClaim)
        // spec [CL-5]: no output check
    }

    /** Sum of lovelace over the outputs whose payment key hash is `pkh`. The staking part of the
      * address is ignored. Spec: "host output sum" and "guest output sum" in SPEC.md, Definitions.
      */
    def sumPaidTo(outputs: List[TxOut], pkh: PubKeyHash): BigInt =
        outputs.foldLeft(BigInt(0)) { (acc, out) =>
            out.address.credential.pubKeyOption match
                case Option.Some(paid) =>
                    if paid === pkh then acc + Value.getLovelace(out.value)
                    else acc
                case Option.None => acc
        }
}
