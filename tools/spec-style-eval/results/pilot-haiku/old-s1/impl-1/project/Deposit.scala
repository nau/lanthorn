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

    // spec [2.2] constants, fixed in the validator
    inline def LATE_CUTOFF: BigInt = BigInt(3_600_000)
    inline def GRACE: BigInt = BigInt(21_600_000)
    inline def MIN_FEE: BigInt = BigInt(2_000_000)
    inline def MAX_BPS: BigInt = BigInt(5000)
    inline def BPS_BASE: BigInt = BigInt(10000)

    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit = {
        // spec [GEN-1] fail when datum is None; [GEN-2] fail when it does not decode as Booking.
        // [GEN-7] no other field of the booking is checked.
        val booking = datum.getOrFail(MissingDatum).to[Booking]
        // spec [GEN-3] fail when the redeemer does not decode as Action.
        val action = redeemer.to[Action]
        // spec [GEN-4] fail when ownRef matches no input.
        val ownInput = tx.findInputOrFail(ownRef, OwnInputNotFound)
        val ownCred = ownInput.resolved.address.credential
        // spec [GEN-5] fail when the own input has no script credential.
        ownCred.scriptHashOrFail(OwnInputNotScript)
        // spec [GEN-6] exactly one input has the same script credential as the own input.
        // Only tx.inputs counts, reference inputs do not (spec 9).
        tx.inputs.findUniqueOrFail(_.resolved.address.credential === ownCred, DoubleSatisfaction)
        // spec [GEN-8] only lovelace counts as deposit.
        val deposit = ownInput.resolved.value.getLovelace

        // spec [GEN-9] the transaction is accepted only when the checks of the selected action hold.
        action match
            case Action.Cancel =>
                // spec [CAN-1] and [CAN-10]: the guest must sign; the host alone is not enough.
                require(tx.isSignedBy(booking.guest), GuestMustSign)
                // spec [CAN-2] U must be finite.
                val u = upperBound(tx).getOrFail(UpperBoundRequired)
                // spec [CAN-9] the free window is applied first.
                // spec [CAN-3] free cancel: no output check, [CAN-8] nothing else is checked.
                if u <= booking.cancelBy then ()
                else
                    // spec [CAN-4] late window, [CAN-5] fail after the late window.
                    require(u <= booking.checkIn - LATE_CUTOFF, CancelClosed)
                    // spec [CAN-6] the host receives exactly the fee.
                    require(
                      paidToKey(tx, booking.host) === lateFee(deposit, booking.feeBps),
                      FeeMismatch
                    )
            case Action.Release =>
                // spec [REL-1] the host must sign.
                require(tx.isSignedBy(booking.host), HostMustSign)
                // spec [REL-2] and [REL-5] at least the deposit goes to the guest.
                // spec [REL-3] and [REL-4]: no validity range check and no guest signature.
                require(paidToKey(tx, booking.guest) >= deposit, GuestUnderpaid)
            case Action.Claim =>
                // spec [CLM-1] the host must sign.
                require(tx.isSignedBy(booking.host), HostMustSign)
                // spec [CLM-2] L must be finite.
                val l = lowerBound(tx).getOrFail(LowerBoundRequired)
                // spec [CLM-3] strict: L must be after checkIn + GRACE.
                // spec [CLM-4] and [CLM-5]: no U check, no output check.
                require(l > booking.checkIn + GRACE, ClaimTooEarly)
    }

    /** spec [2.1] L: absent when the lower bound is not finite. Inclusive bound is taken as is,
      * exclusive bound plus 1.
      */
    inline def lowerBound(tx: TxInfo): Option[PosixTime] = {
        val b = tx.validRange.from
        b.boundType match
            case IntervalBoundType.Finite(t) =>
                Option.Some(if b.isInclusive then t else t + BigInt(1))
            case _ => Option.None
    }

    /** spec [2.1] U: absent when the upper bound is not finite. Exclusive bound is taken as is,
      * inclusive bound plus 1.
      */
    inline def upperBound(tx: TxInfo): Option[PosixTime] = {
        val b = tx.validRange.to
        b.boundType match
            case IntervalBoundType.Finite(t) =>
                Option.Some(if b.isInclusive then t + BigInt(1) else t)
            case _ => Option.None
    }

    /** spec 2 "paid to X": sum of lovelace over outputs whose payment credential is
      * PubKeyCredential(key). The staking part is ignored, so this does not use valuePaidTo, which
      * compares the whole address.
      */
    inline def paidToKey(tx: TxInfo, key: PubKeyHash): BigInt =
        tx.outputs.foldLeft(BigInt(0)) { (acc, out) =>
            if out.address.credential === Credential.PubKeyCredential(key) then
                acc + out.value.getLovelace
            else acc
        }

    /** spec [7] fee for a late cancel, steps [FEE-1] to [FEE-4] in order. */
    inline def lateFee(deposit: BigInt, feeBps: BigInt): BigInt = {
        // [FEE-1] clamp to MAX_BPS; [FEE-5] no failure above MAX_BPS; [FEE-6] negative is allowed.
        val bps: BigInt = if feeBps > MAX_BPS then MAX_BPS else feeBps
        // [FEE-2] round up.
        val raw = (deposit * bps) divCeil BPS_BASE
        // [FEE-3] at least MIN_FEE.
        val floored = if raw > MIN_FEE then raw else MIN_FEE
        // [FEE-4] never more than the deposit.
        if floored < deposit then floored else deposit
    }

    // Error messages
    inline val MissingDatum = "Datum is missing"
    inline val OwnInputNotFound = "Own input not found"
    inline val OwnInputNotScript = "Own input is not at a script address"
    inline val DoubleSatisfaction = "Exactly one input from this script is allowed"
    inline val GuestMustSign = "Guest must sign"
    inline val HostMustSign = "Host must sign"
    inline val UpperBoundRequired = "Upper bound of the validity range must be finite"
    inline val LowerBoundRequired = "Lower bound of the validity range must be finite"
    inline val CancelClosed = "Cancel is not possible in the last hour before check-in"
    inline val FeeMismatch = "Host must receive exactly the cancellation fee"
    inline val GuestUnderpaid = "Guest must receive at least the deposit"
    inline val ClaimTooEarly = "Claim is only possible after check-in plus grace"
}
