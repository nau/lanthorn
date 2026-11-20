package validator

import scalus.compiler.Compile
import scalus.uplc.builtin.Data
import scalus.uplc.builtin.Data.toData
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

    // Constants from the specification, "Definitions".
    inline def NoticePeriod: BigInt = BigInt(3_600_000)
    inline def GracePeriod: BigInt = BigInt(21_600_000)
    inline def MinimumFee: BigInt = BigInt(2_000_000)
    inline def MaximumBps: BigInt = BigInt(5000)
    inline def BpsDenominator: BigInt = BigInt(10_000)

    // Error messages
    inline val MissingDatum = "Datum is missing"
    inline val OwnInputNotFound = "Own input not found"
    inline val MultipleDepositInputs = "Must spend exactly one deposit"
    inline val UnsignedByGuest = "Must be signed by the guest"
    inline val UnsignedByHost = "Must be signed by the host"
    inline val UpperBoundMustBeFinite = "Upper bound must be finite"
    inline val LowerBoundMustBeFinite = "Lower bound must be finite"
    inline val TooLateToCancel = "Too late to cancel"
    inline val WrongLateFee = "Host must receive exactly the fee"
    inline val GuestUnderpaid = "Guest must receive at least the deposit"
    inline val TooEarlyToClaim = "Claim is not yet allowed"

    /** Sum of lovelace over the outputs paid to a payment key hash. The staking
      * part of the address is ignored. [spec: Definitions, host output sum /
      * guest output sum]
      */
    inline def paidToKey(outputs: List[TxOut], pkh: PubKeyHash): BigInt =
        val wanted = Credential.PubKeyCredential(pkh).toData
        outputs.foldLeft(BigInt(0)) { (acc, out) =>
            if out.address.credential.toData == wanted then
                acc + Value.getLovelace(out.value)
            else acc
        }

    /** Fee on a late cancel. [spec: CA-7, CA-8, CA-9, CA-10, CA-11, CA-12]
      */
    inline def lateFee(deposit: BigInt, feeBps: BigInt): BigInt =
        val capped =
            if feeBps > MaximumBps then MaximumBps else feeBps // spec [CA-7]
        val rate = if capped < 0 then BigInt(0) else capped // spec [CA-8]
        val raw = Math.divCeil(deposit * rate, BpsDenominator) // spec [CA-9]
        val raised =
            if raw < MinimumFee then MinimumFee else raw // spec [CA-10]
        if raised > deposit then deposit else raised // spec [CA-11]

    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit = {
        val booking =
            datum.getOrFail(MissingDatum).to[Booking] // spec [SP-1] [SP-2]
        val action = redeemer.to[Action] // spec [SP-3]

        val ownInput =
            tx.findInputOrFail(ownRef, OwnInputNotFound) // spec [SP-4]
        val ownCredential = ownInput.resolved.address.credential
        // Exactly one input at the deposit script, the own input. spec [SP-5] [SP-6]
        tx.inputs.findUniqueOrFail(
          input =>
              input.resolved.address.credential.toData == ownCredential.toData,
          MultipleDepositInputs
        )
        val deposit = Value.getLovelace(ownInput.resolved.value) // spec [SP-7]

        action match
            case Action.Cancel =>
                // spec [CA-1]
                require(tx.isSignedBy(booking.guest), UnsignedByGuest)
                // spec [CA-2] [CA-3]
                val until = tx.validToOrFail(UpperBoundMustBeFinite)
                // spec [CA-4] [CA-5] [CA-15]: free cancel, no output check
                if until <= booking.cancelBy then ()
                else
                    // spec [CA-6]
                    require(
                      until <= booking.checkIn - NoticePeriod,
                      TooLateToCancel
                    )
                    // spec [CA-13] [CA-14]: exact fee to host, guest not checked
                    require(
                      paidToKey(tx.outputs, booking.host) === lateFee(
                        deposit,
                        booking.feeBps
                      ),
                      WrongLateFee
                    )
            case Action.Release =>
                // spec [RE-1]
                require(tx.isSignedBy(booking.host), UnsignedByHost)
                // spec [RE-2] [RE-4] [RE-3]: no validity check
                require(
                  paidToKey(tx.outputs, booking.guest) >= deposit,
                  GuestUnderpaid
                )
            case Action.Claim =>
                // spec [CL-1]
                require(tx.isSignedBy(booking.host), UnsignedByHost)
                // spec [CL-2] [CL-4]
                val from = tx.validFromOrFail(LowerBoundMustBeFinite)
                // spec [CL-3]: strict, after checkIn + grace; no output check [CL-5]
                require(from > booking.checkIn + GracePeriod, TooEarlyToClaim)
    }
}
