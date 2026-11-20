package validator

import scalus.compiler.Compile
import scalus.uplc.builtin.Data
import scalus.uplc.builtin.Data.{fromData, toData}
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

    val LateCutoff: BigInt = BigInt(3_600_000)
    val Grace: BigInt = BigInt(21_600_000)
    val MinFee: BigInt = BigInt(2_000_000)
    val MaxBps: BigInt = BigInt(5000)
    val BpsBase: BigInt = BigInt(10000)

    // spec [2.1] L: lower time, absent when not finite
    def lowerTime(r: Interval): Option[BigInt] =
        r.from.boundType match
            case IntervalBoundType.Finite(t) =>
                if r.from.isInclusive then Option.Some(t) else Option.Some(t + 1)
            case _ => Option.None

    // spec [2.1] U: upper time, absent when not finite
    def upperTime(r: Interval): Option[BigInt] =
        r.to.boundType match
            case IntervalBoundType.Finite(t) =>
                if r.to.isInclusive then Option.Some(t + 1) else Option.Some(t)
            case _ => Option.None

    // spec [2.] "paid to X": lovelace over outputs with PubKeyCredential(X)
    def paidTo(tx: TxInfo, x: PubKeyHash): BigInt =
        tx.outputs.foldLeft(BigInt(0)) { (acc, out) =>
            out.address.credential match
                case Credential.PubKeyCredential(h) =>
                    if h.toData == x.toData then acc + out.value.getLovelace else acc
                case _ => acc
        }

    // spec [FEE-1..FEE-4]
    def fee(deposit: BigInt, feeBps: BigInt): BigInt = {
        val bps = if feeBps < MaxBps then feeBps else MaxBps // [FEE-1]
        val raw = (deposit * bps).divCeil(BpsBase) // [FEE-2]
        val floored = if raw > MinFee then raw else MinFee // [FEE-3]
        if floored < deposit then floored else deposit // [FEE-4]
    }

    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit = {
        // spec [GEN-1] missing datum fails
        val d = datum.getOrFail("no datum")
        // spec [GEN-2] undecodable datum fails; [GEN-7] no field checks
        val booking = d.to[Booking]
        // spec [GEN-3] undecodable redeemer fails
        val action = redeemer.to[Action]
        // spec [GEN-4] own input must exist
        val ownInput = tx.findInputOrFail(ownRef, "own input not found")
        // spec [GEN-5] script credential required
        val scriptHash = ownInput.resolved.address.credential
            .scriptHashOrFail("own input not at script address")
        // spec [GEN-6] exactly one input with this script credential
        val sameScript = tx.inputs.count { i =>
            i.resolved.address.credential match
                case Credential.ScriptCredential(h) => h.toData == scriptHash.toData
                case _                              => false
        }
        require(sameScript === BigInt(1), "exactly one deposit input required")
        // spec [GEN-8] only lovelace counts
        val deposit = ownInput.resolved.value.getLovelace

        // spec [GEN-9] only the selected action's checks apply
        action match
            case Action.Cancel =>
                // spec [CAN-1], [CAN-10]
                require(tx.signatories.contains(booking.guest), "guest signature required")
                // spec [CAN-2]
                val u = upperTime(tx.validRange).getOrFail("upper bound required")
                // spec [CAN-3], [CAN-9] free window first; [CAN-7], [CAN-8] no other checks
                if u <= booking.cancelBy then ()
                else
                    // spec [CAN-5]
                    require(u <= booking.checkIn - LateCutoff, "cancel too late")
                    // spec [CAN-4], [CAN-6] exact fee to host
                    require(
                      paidTo(tx, booking.host) === fee(deposit, booking.feeBps),
                      "host must be paid the fee exactly"
                    )
            case Action.Release =>
                // spec [REL-1]; [REL-4] guest signature not required; [REL-3] no time check
                require(tx.signatories.contains(booking.host), "host signature required")
                // spec [REL-2], [REL-5] at least the deposit
                require(paidTo(tx, booking.guest) >= deposit, "guest must get the deposit")
            case Action.Claim =>
                // spec [CLM-1]
                require(tx.signatories.contains(booking.host), "host signature required")
                // spec [CLM-2]
                val l = lowerTime(tx.validRange).getOrFail("lower bound required")
                // spec [CLM-3]; [CLM-4], [CLM-5] no U or output checks
                require(l > booking.checkIn + Grace, "grace period not over")
    }
}
