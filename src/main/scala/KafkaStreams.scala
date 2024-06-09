import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}
import org.apache.kafka.streams.kstream.{GlobalKTable, JoinWindows}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{KStream, KTable}
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.scala.ImplicitConversions._

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Properties

object KafkaStreams {

  object Domain {
    type UserId = String
    type Profile = String
    type Product = String
    type OrderId = String
    type Status = String

    case class Order(orderId: OrderId, userId: UserId, products: List[Product], amount: Double) // Double is used for this personal project only

    case class Discount(profile: Profile, amount: Double)

    case class Payment(orderId: OrderId, status: Status)
  }

  object Topics {
    val OrdersByUser = "orders-by-user"
    val DiscountProfilesByUser = "discount-profiles-by-user"
    val Discounts = "discounts"
    val Orders = "orders"
    val Payments = "payments"
    val PaidOrders = "paid-orders"
  }

  import Domain._
  import Topics._

  implicit def serde[A >: Null : Decoder : Encoder]: Serde[A] = {
    val serializer = (a: A) => a.asJson.noSpaces.getBytes()
    val deserializer = (bytes: Array[Byte]) => {
      val string = new String(bytes)
      decode[A](string).toOption
    }
    Serdes.fromFn[A](serializer = serializer, deserializer = deserializer)

  }

  def main(args: Array[String]): Unit = {
    // topology
    val builder = new StreamsBuilder()
    //KStream
    val usersOrdersStream = builder.stream[UserId, Order](OrdersByUser)
    //Ktable
    val userProfilesTable: KTable[UserId, Profile] = builder.table[UserId, Profile](DiscountProfilesByUser)
    val userProfilesGTable: GlobalKTable[Profile, Discount] = builder.globalTable[Profile, Discount](Discounts)
    //Ktransformation
    val expensiveOrders: KStream[UserId, Order] = usersOrdersStream.filter {
      (_, order) => order.amount > 1000
    }

    val listOfProducts: KStream[UserId, List[Product]] = usersOrdersStream.mapValues {
      order => order.products
    }

    val productsStream: KStream[UserId, Product] = usersOrdersStream.flatMapValues(_.products)
    //join
    val ordersWithUsersProfiles: KStream[UserId, (Order, Profile)] = usersOrdersStream.join(userProfilesTable) {
      (order, profile) => (order, profile)
    }
    val discountedOrdersStream = ordersWithUsersProfiles.join(userProfilesGTable)(
      { case (userId, (order, profile)) => profile },
      { case ((order, profile), discount) => order.copy(amount = order.amount - discount.amount) }
    )
    //change the identifier
    val ordersStream: KStream[OrderId, Order] = discountedOrdersStream.selectKey((userId, order) => order.orderId)
    val paymentsStream: KStream[OrderId, Payment] = builder.stream[OrderId, Payment](Payments)

    val joinWindow: JoinWindows = JoinWindows.of(Duration.of(5, ChronoUnit.MINUTES))
    val joinOrdersPayments = (order: Order, payment: Payment) => if (payment.status == "PAID") Option(order) else Option.empty[Order]

    val ordersPaid = ordersStream.join(paymentsStream)(joinOrdersPayments, joinWindow).flatMapValues(maybeorder => maybeorder.toList)

    //send the data to a topic
    ordersPaid.to(PaidOrders)

    val topology = builder.build()

    val props = new Properties
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "orders-app")
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.stringSerde.getClass)
    val application = new KafkaStreams(topology, props)
    application.start()

  }

}
