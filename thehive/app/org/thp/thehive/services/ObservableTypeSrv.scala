package org.thp.thehive.services

import akka.actor.typed.ActorRef
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.{BadRequestError, CreateError, EntityIdOrName}
import org.thp.thehive.models._

import scala.util.{Failure, Success, Try}

class ObservableTypeSrv(observableSrv: => ObservableSrv, integrityCheckActor: => ActorRef[IntegrityCheck.Request])
    extends VertexSrv[ObservableType]
    with TheHiveOpsNoDeps {

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[ObservableType] =
    startTraversal.getByName(name)

  override def exists(e: ObservableType)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  override def createEntity(e: ObservableType)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] = {
    integrityCheckActor ! IntegrityCheck.EntityAdded("ObservableType")
    super.createEntity(e)
  }

  def create(observableType: ObservableType)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] =
    if (exists(observableType))
      Failure(CreateError(s"Observable type ${observableType.name} already exists"))
    else
      createEntity(observableType)

  def remove(idOrName: EntityIdOrName)(implicit graph: Graph): Try[Unit] =
    if (!isUsed(idOrName)) Success(get(idOrName).remove())
    else Failure(BadRequestError(s"Observable type $idOrName is used"))

  def isUsed(idOrName: EntityIdOrName)(implicit graph: Graph): Boolean =
    get(idOrName)
      .value(_.name)
      .headOption
      .fold(false)(ot => observableSrv.startTraversal.has(_.dataType, ot).exists)

  def useCount(idOrName: EntityIdOrName)(implicit graph: Graph): Long =
    get(idOrName)
      .value(_.name)
      .headOption
      .fold(0L)(ot => observableSrv.startTraversal.has(_.dataType, ot).getCount)
}

trait ObservableTypeOps { _: TheHiveOpsNoDeps =>

  implicit class ObservableTypeObs(traversal: Traversal.V[ObservableType]) {

    def get(idOrName: EntityIdOrName): Traversal.V[ObservableType] =
      idOrName.fold(traversal.getByIds(_), getByName)

    def getByName(name: String): Traversal.V[ObservableType] = traversal.has(_.name, name)
  }
}

class ObservableTypeIntegrityCheck(val db: Database, val service: ObservableTypeSrv) extends DedupCheck[ObservableType]
