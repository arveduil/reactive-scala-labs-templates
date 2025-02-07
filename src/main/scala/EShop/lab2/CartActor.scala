package EShop.lab2

import java.util.concurrent.TimeUnit

import EShop.lab2.CartActor.{AddItem, GetItems}
import EShop.lab2.Checkout.CheckOutClosed
import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command
  case object GetItems             extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event
  case class CheckoutNotStarted()                   extends Event
  case class ItemAdded(item: Any)                   extends Event
  case class ItemRemoved(item: Any)                 extends Event
  case class CartExpired()                          extends Event

  def props() = Props(new CartActor())
}

class CartActor extends Actor with Timers {
import CartActor._
val system = akka.actor.ActorSystem("system")
  import system.dispatcher


  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5000 seconds

  private def scheduleTimer: Cancellable = system.scheduler.scheduleOnce( cartTimerDuration, self, ExpireCart)


  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart(Seq(item)), scheduleTimer)
    case GetItems =>
      sender() ! Cart.empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
        timer.cancel()
        context become nonEmpty(cart.addItem(item), scheduleTimer)
    case ExpireCart =>
        timer.cancel()
        context become empty
    case RemoveItem(item)  if cart.removeItem(item).isEmpty()  =>
         timer.cancel()
         context become empty
    case RemoveItem(item) if cart.contains(item) =>
          timer.cancel()
          context become nonEmpty(cart.removeItem(item), scheduleTimer)

    case StartCheckout =>{
     // timer.cancel()
      val checkoutRef = system.actorOf(Checkout.props(self), "checkoutActor")
      checkoutRef ! Checkout.StartCheckout
      sender() ! CartActor.CheckoutStarted(checkoutRef)
      context become inCheckout(cart)
    }
    case GetItems =>
      sender() ! cart
  }

  def inCheckout(cart: Cart): Receive = {
    case CancelCheckout =>
      context become nonEmpty(cart,scheduleTimer)

    case CloseCheckout =>
      context.parent ! CloseCheckout
      context become empty

    case CheckOutClosed =>
      context.parent ! CloseCheckout
      context become empty
  }
}
