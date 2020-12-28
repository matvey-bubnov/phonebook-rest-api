package controllers

import java.io.FileInputStream
import java.util.Properties

import akka.actor.ActorSystem
import javax.inject._
import models._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, _}
import play.api.routing.JavaScriptReverseRouter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {
  implicit val todoFormat = Json.format[Phone]

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok( views.html.index("Phonebook") )
  }

  val phoneForm: Form[PhoneForm] = Form(
    mapping(
      "phoneNumber" -> text,
      "name" -> text
    )(PhoneForm.apply)(PhoneForm.unapply))

  lazy val dbUrl =
    try {
      val prop = new Properties()
      prop.load(new FileInputStream("application.properties"))
      prop.getProperty("url")
    } catch { case e: Exception =>
      HomeController.logger.error("Properties file cannot be loaded")
      e.printStackTrace()
      sys.exit(1)
    }

  def addPhone()= Action { implicit request =>
    phoneForm.bindFromRequest.fold(
      _ => { BadRequest(views.html.index("Phonebook")) },
      phone => {
        DB.add(dbUrl, phone)
        Redirect(routes.HomeController.index)
      })
  }

  def getPhones() = Action {
    Ok(Json.toJson(DB.all(dbUrl)))
  }

  def modPhone(id: Long)= Action { implicit request =>
    phoneForm.bindFromRequest.fold(
      _ => { BadRequest(views.html.index("Phonebook")) },
      phone => {
        DB.mod(dbUrl, id, phone)
        Redirect(routes.HomeController.index)
      })
  }

  def delPhone(id: Long) = Action {
    DB.del(dbUrl, id)
    Ok
  }

  def byName(nameSubstring: String) = Action {
    Ok(
      Json.toJson(
        DB.nameLike(dbUrl, nameSubstring)
      )
    )
  }

  def byPhone(phoneSubstring: String) = Action {
    Ok(
      Json.toJson(
        DB.phoneLike(dbUrl, phoneSubstring)
      )
    )
  }

  def delByTime() = Action {
    DB.delByTime(dbUrl)
    Ok
  }

  def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.HomeController.addPhone,
        routes.javascript.HomeController.getPhones,
        routes.javascript.HomeController.modPhone,
        routes.javascript.HomeController.delPhone,
        routes.javascript.HomeController.byName,
        routes.javascript.HomeController.byPhone
      )).as("text/javascript")
  }

  val system: ActorSystem = ActorSystem()
  system.scheduler.scheduleAtFixedRate(initialDelay = 1.seconds, interval = 1.days) {
    new Runnable() {
      override def run(): Unit = {
        DB.delByTime(dbUrl)
      }
    }
  }

}

object HomeController {
  val logger: Logger = Logger(this.getClass())
}