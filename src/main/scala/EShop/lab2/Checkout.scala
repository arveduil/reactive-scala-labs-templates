package EShop.lab2

import EShop.lab2.Checkout._
import EShop.lab3.Payment
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(
                cartActor: ActorRef
              ) extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      val timer = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)(context.system.dispatcher)
      context become selectingDelivery(timer)
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpireCheckout => context become cancelled
    case SelectDeliveryMethod(deliveryMethod) =>
      log.info("Selected delivery method: ", deliveryMethod)
      context become selectingPaymentMethod(timer)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpireCheckout => context become cancelled
    case SelectPayment(paymentMethod) =>
      timer.cancel()
      val payment = context.actorOf(Payment.props(paymentMethod, sender, self), "paymentActor")
      sender ! PaymentStarted(payment)
      val paymentTimer = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)(context.system.dispatcher)
      context become processingPayment(paymentTimer)
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpirePayment => context become cancelled
    case ReceivePayment =>
      timer.cancel()
      cartActor ! CartActor.CloseCheckout
      context become closed
  }

  def cancelled: Receive = LoggingReceive {
    case msg => log.info("Checkout cancelled. Message: ", msg)
  }

  def closed: Receive = LoggingReceive {
    case msg =>
      log.info("Checkout closed. Message: ", msg)
  }

}
