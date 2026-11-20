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

    val FeeCapBps: BigInt = BigInt(5_000)
    val BpsDenominator: BigInt = BigInt(10_000)
    val FeeFloor: BigInt = BigInt(2_000_000)
    val CancelNotice: BigInt = BigInt(3_600_000)
    val GracePeriod: BigInt = BigInt(21_600_000)

    /** Finite value of a bound, or None when absent. No inclusive/exclusive adjustment. */
    // spec [SP-6] [SP-7] [SP-8]
    def finiteTime(b: IntervalBound): Option[PosixTime] =
        b.boundType match
            case IntervalBoundType.Finite(t) => Option.Some(t)
            case _                           => Option.None

    /** Fee of a Late Cancel. */
    // spec [FE-1] [FE-2] [FE-3] [FE-4] [FE-5]
    def fee(deposit: BigInt, feeBps: BigInt): BigInt =
        val bps = if feeBps > FeeCapBps then FeeCapBps else feeBps // FE-1
        val raw = (deposit * bps).divCeil(BpsDenominator) // FE-2
        val floored = if raw < FeeFloor then FeeFloor else raw // FE-3
        if floored > deposit then deposit else floored // FE-4

    /** Sum of lovelace over outputs paid to the public-key payment credential `key`. */
    def sumTo(outputs: List[TxOut], key: PubKeyHash): BigInt =
        outputs.foldLeft(BigInt(0)) { (acc, o) =>
            o.address.credential match
                case Credential.PubKeyCredential(h) =>
                    if h === key then acc + o.value.getLovelace else acc
                case _ => acc
        }

    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit =
        // spec [SP-1]
        val booking = datum.getOrFail("datum absent").to[Booking] // spec [SP-2]
        val action = redeemer.to[Action] // spec [SP-3]

        // spec [SP-4] exactly one input at the own address
        val ownInput = tx.inputs.findUniqueOrFail(_.outRef === ownRef, "own input not found")
        val ownAddress = ownInput.resolved.address
        tx.inputs.findUniqueOrFail(_.resolved.address === ownAddress, "not exactly one script input")
        // spec [SP-5] [SP-9] lovelace only
        val deposit = ownInput.resolved.value.getLovelace

        action match
            case Action.Cancel =>
                // spec [CN-1]
                require(tx.isSignedBy(booking.guest), "guest must sign")
                // spec [CN-2] [CN-3] only U is used
                val u = finiteTime(tx.validRange.to).getOrFail("upper bound absent")
                if u <= booking.cancelBy then () // spec [CN-4] free, no output check
                else if u <= booking.checkIn - CancelNotice then
                    // spec [CN-5] exact match with fee; [CN-7] nothing else constrained
                    require(
                      sumTo(tx.outputs, booking.host) === fee(deposit, booking.feeBps),
                      "host output sum must equal fee"
                    )
                else fail("too late to cancel") // spec [CN-6]
            case Action.Release =>
                // spec [RL-1]
                require(tx.isSignedBy(booking.host), "host must sign")
                // spec [RL-3] [RL-2] [RL-4] [EX-2]: no range check, only guest sum
                require(sumTo(tx.outputs, booking.guest) >= deposit, "guest must get deposit")
            case Action.Claim =>
                // spec [CL-1]
                require(tx.isSignedBy(booking.host), "host must sign")
                // spec [CL-2] [CL-4] [CL-5]: only L is read, no outputs
                val l = finiteTime(tx.validRange.from).getOrFail("lower bound absent")
                // spec [CL-3] strict
                require(l > booking.checkIn + GracePeriod, "grace period not over")
        // spec [SP-10] reaching here means every applicable check passed
}
