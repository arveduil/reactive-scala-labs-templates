package EShop.lab2


import EShop.lab2.CartFSM.Status
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }

  def props() = Props(new CartFSM())
}



class CartFSM extends LoggingFSM[Status.Value, Cart] {
  import EShop.lab2.CartFSM.Status._
  import EShop.lab2.CartActor._
  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val cartTimerDuration: FiniteDuration = 1 seconds

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), Cart(Seq())) =>
      goto(NonEmpty) using Cart(Seq(item))
  }

  when(NonEmpty, stateTimeout = cartTimerDuration) {
    case Event(AddItem(item), cart: Cart) => stay using cart.addItem(item)
    case Event(RemoveItem(item), cart: Cart) if cart.removeItem(item).isEmpty() => goto(Empty) using Cart(Seq())
    case Event(RemoveItem(item), cart: Cart) if cart.contains(item) => stay using cart.removeItem(item)
    case Event(StartCheckout, cart: Cart) if !cart.isEmpty() => goto(InCheckout) using cart
    case Event(StateTimeout, _) =>goto(Empty) using Cart(Seq())
  }

  when(InCheckout) {
    case Event(CancelCheckout, cart: Cart) => goto(NonEmpty) using cart
    case Event(CloseCheckout, _ ) => goto(Empty) using Cart(Seq())
  }

}
