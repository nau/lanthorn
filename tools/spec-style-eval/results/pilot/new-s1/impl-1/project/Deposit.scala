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
    inline override def spend(
        datum: Option[Data],
        redeemer: Data,
        tx: TxInfo,
        ownRef: TxOutRef
    ): Unit = {
        val noticePeriod: BigInt = 3_600_000
        val gracePeriod: BigInt = 21_600_000
        val minimumFee: BigInt = 2_000_000
        val maximumBps: BigInt = 5000

        def lovelacePaidTo(tx: TxInfo, key: PubKeyHash): BigInt =
            tx.outputs.foldLeft(BigInt(0)) { (acc, out) =>
                out.address.credential match
                    case Credential.PubKeyCredential(h) =>
                        if h === key then acc + out.value.getLovelace else acc
                    case _ => acc
            }
        // spec [SP-1]
        val booking = datum.getOrFail("no datum").to[Booking]
        // spec [SP-2]
        // spec [SP-3]
        val action = redeemer.to[Action]
        // spec [SP-4]
        val own = tx.findInputOrFail(ownRef, "own input not found")
        // spec [SP-7]
        val deposit = own.resolved.value.getLovelace
        val ownCred = own.resolved.address.credential
        // spec [SP-5] [SP-6]
        tx.inputs.findUniqueOrFail(
          _.resolved.address.credential === ownCred,
          "exactly one deposit input required"
        )
        // spec [SP-8]
        action match
            case Action.Cancel =>
                // spec [CA-1]
                require(tx.isSignedBy(booking.guest), "guest must sign")
                // spec [CA-2] [CA-3]
                val u =
                    tx.validRange.to.finiteOrFail("upper bound must be finite")
                // spec [CA-4] [CA-5] [CA-15]
                if u <= booking.cancelBy then ()
                else {
                    // spec [CA-6]
                    require(
                      u <= booking.checkIn - noticePeriod,
                      "cancel too late"
                    )
                    // spec [CA-7] [CA-8]
                    val rate =
                        if booking.feeBps < 0 then BigInt(0)
                        else if booking.feeBps > maximumBps then maximumBps
                        else booking.feeBps
                    // spec [CA-9]
                    val raw = (deposit * rate).divCeil(10000)
                    // spec [CA-10]
                    val atLeastMin =
                        if raw < minimumFee then minimumFee else raw
                    // spec [CA-11] (order per [CA-12])
                    val fee =
                        if atLeastMin > deposit then deposit else atLeastMin
                    // spec [CA-13] [CA-14]
                    require(
                      lovelacePaidTo(tx, booking.host) === fee,
                      "host must receive exact fee"
                    )
                }
            case Action.Release =>
                // spec [RE-1]
                require(tx.isSignedBy(booking.host), "host must sign")
                // spec [RE-2] [RE-3] [RE-4]
                require(
                  lovelacePaidTo(tx, booking.guest) >= deposit,
                  "guest must receive deposit"
                )
            case Action.Claim =>
                // spec [CL-1]
                require(tx.isSignedBy(booking.host), "host must sign")
                // spec [CL-2] [CL-4] [CL-5]
                val l = tx.validRange.from.finiteOrFail(
                  "lower bound must be finite"
                )
                // spec [CL-3]
                require(
                  l > booking.checkIn + gracePeriod,
                  "grace period not over"
                )
    }
}
