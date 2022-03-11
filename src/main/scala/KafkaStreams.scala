import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.kstream.GlobalKTable
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{KStream, KTable}
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.scala.ImplicitConversions._
object KafkaStreams {

  object Domain {
    type UserId = String
    type Profile = String
    type Product = String
    type OrderId = String
    type Status = String

    case class Order(orderId:OrderId,userId:UserId,products:List[Product],amount: Double)
    case class Discount(profile: Profile,amount:Double)
    case class Payment(orderId: OrderId,status: Status)
  }
    object Topics {
      val OrdersByUser = "orders-by-user"
      val DiscountProfilesByUser = "discount-profiles-by-user"
      val Discounts ="discounts"
      val Orders = "orders"
      val Payments ="payments"
      val PaidOrders="paid-orders"
    }

  import Domain._
  import Topics._
  implicit  def serde[A>: Null : Decoder: Encoder]: Serde[A] = {
    val serializer = (a: A )=> a.asJson.noSpaces.getBytes()
    val deserializer = (bytes : Array[Byte]) => {
      val string = new String(bytes)
      decode[A](string).toOption
    }
    Serdes.fromFn[A](serializer,deserializer)

  }
  // topology
  val builder = new StreamsBuilder()
  //KStream
  val usersOrdersStream : KStream[UserId,Order] = builder.stream[UserId,Order](OrdersByUser)
  //Ktable
  val userProfilesTable : KTable[UserId,Profile] = builder.table[UserId,Profile](DiscountProfilesByUser)
  val userProfilesGTable : GlobalKTable[Profile,Discount] = builder.globalTable[Profile,Discount](Discounts)
  //Ktransformation
  val expensiveOrders: KStream[UserId, Order] = usersOrdersStream.filter {
    (userid,order) => order.amount > 1000
  }

  val listOfProducts: KStream[UserId, List[Product]] = usersOrdersStream.mapValues{
    order => order.products
  }
  val productsStream: KStream[UserId, Product] = usersOrdersStream.flatMapValues(_.products)
  //join
  val ordersWithUsersProfiles: KStream[UserId, (Order, Profile)] = usersOrdersStream.join(userProfilesTable) {
    (order,profile) => (order,profile)
  }

  builder.build()
  def main(args: Array[String]) : Unit ={
  }

}
