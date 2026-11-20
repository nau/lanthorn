package validator

import scalus.cardano.blueprint.{Blueprint, Contract}
import scalus.cardano.onchain.plutus.v3.PubKeyHash
import scalus.compiler.Options
import scalus.uplc.PlutusV3

/** Bundles the compiled validator with a CIP-57 blueprint.
  *
  * The Scalus compiler plugin discovers `Contract` objects, and the
  * `scalus-sbt-plugin` exposes them to the `blueprint` and `deploy` sbt tasks.
  */
object DepositContract extends Contract {
    private given Options = Options.release
    lazy val compiled = PlutusV3.compile(Deposit.validate)
    lazy val blueprint = Blueprint.plutusV3[PubKeyHash, Unit](
      title = "Deposit",
      description = "A Plutus V3 validator scaffold.",
      version = "1.0.0",
      license = Some("Apache License Version 2.0"),
      compiled = compiled
    )
}
