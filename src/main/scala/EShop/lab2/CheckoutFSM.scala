package EShop.lab2

import EShop.lab2.Checkout.Data
import EShop.lab2.CheckoutFSM.Status
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM(cartActor))
}

class CheckoutFSM(cartActor: ActorRef) extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.CheckoutFSM.Status._
  import EShop.lab2.Checkout._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private val scheduler = context.system.scheduler

  startWith(NotStarted, Uninitialized)

  when(NotStarted) {
    case Event(StartCheckout,Uninitialized) =>
      goto(SelectingDelivery) using Uninitialized
  }

  when(SelectingDelivery, stateTimeout = checkoutTimerDuration) {
    case Event(ExpireCheckout | CancelCheckout, _) =>
      goto(Cancelled)
    case Event(SelectDeliveryMethod(_),deliveryMethod) =>
      goto(SelectingPaymentMethod) using deliveryMethod
    case Event(StateTimeout,_) =>
      goto(Cancelled) using Uninitialized
  }

  when(SelectingPaymentMethod, stateTimeout = checkoutTimerDuration) {
    case Event(ExpireCheckout | CancelCheckout, _) =>
      goto(Cancelled) using Uninitialized
    case Event(SelectPayment(_), data) =>
      goto(ProcessingPayment) using data
    case Event(StateTimeout,_) =>
      goto(Cancelled) using Uninitialized
  }

  when(ProcessingPayment , stateTimeout = checkoutTimerDuration) {
    case Event(ExpirePayment | CancelCheckout, _) =>
      goto(Cancelled) using Uninitialized
    case Event(ReceivePayment, _) =>
      goto(Closed)
    case Event(StateTimeout,_) =>
      goto(Cancelled)
  }

  when(Cancelled) {
    case Event(_,_) => stay
  }

  when(Closed) {
    case Event(_,_) => stay
  }

}
