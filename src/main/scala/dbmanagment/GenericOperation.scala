package dbmanagment
import slick.jdbc.SQLServerProfile.api._
import slick.lifted.{AbstractTable, TableQuery}
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.{universe => runtime}
object Reflection {
  val runtimeMirror: runtime.Mirror = runtime.runtimeMirror(getClass.getClassLoader)
  def getTypeTag[C: runtime.TypeTag]: runtime.TypeTag[C] = runtime.typeTag[C]
  def createClassByConstructor[C: runtime.TypeTag](args: Any*): C =
    runtimeMirror.reflectClass(getTypeTag[C].tpe.typeSymbol.asClass)
      .reflectConstructor(runtime.typeOf[C].decl(runtime.termNames.CONSTRUCTOR)
        .asMethod)(args: _*).asInstanceOf[C]
}
abstract class GenericTable[T](tag: Tag, name: String,nameId:String) extends Table[T](tag, name) {
  def id = column[Int](nameId, O.PrimaryKey, O.AutoInc)
}
abstract class GenericTableQuery[T, C <: AbstractTable[T]: runtime.TypeTag] {
  import Reflection._
  // look at following code: Students, if you want to initialize Students
  // you're gonna need a tag parameter, that's why we pass tag here
  val table = TableQuery.apply(tag => createClassByConstructor[C](tag))
}
trait GenericCRUD[T,C <: GenericTable[T]] extends GenericTableQuery[T,C] with DB[C,T] {
  private val queryById = Compiled((id: Rep[Int]) => table.filter(_.id === id))
  private val queryByIdPlus =  (id: Seq[Int]) => table.filter(_.id.inSet(id))
  def select(id:Int): Future[Option[T]] = run(queryById(id).result.headOption)
  def selectAll: Future[List[T]] = run(table.result).map(result=>result.toList)
  def insert(c: T): Future[Int] =  run((table returning table.map(_.id)) += c)
  def insertAll(c: List[T]): Future[List[Int]] =  run((table returning table.map(_.id)) ++= c).map(result=>result.toList)
  def delete(id:Int): Future[Int] = run(queryById(id).delete)
  def deleteAll(id:List[Int]): Future[Int] = run(queryByIdPlus(id).delete)
  def update(c: T): Future[Int] =run(table.insertOrUpdate(c))
}
object implicitsGeneric{

  import slick.jdbc.SQLServerProfile.api._
  case class Brands[T,C<: GenericTable[T]:runtime.TypeTag]() extends GenericCRUD[T,C] {
    override def selectAll: Future[List[T]] = super.selectAll
    override def select(id: Int): Future[Option[T]] = super.select(id)
    override def insert(elem: T): Future[Int]= super.insert(elem)
    override def insertAll(c: List[T]): Future[List[Int]] =  super.insertAll(c)
    override def delete(id: Int): Future[Int] = super.delete(id)
    override def deleteAll(id: List[Int]): Future[Int] = super.deleteAll(id)
    override def update(elem: T): Future[Int] = super.update(elem)

  }
}