package controllers

import actors.TwitterStreamer
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc._

/**
  * Created by Kurtulus Arkan on 8/16/16.
  */
class Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index("Tweets."))
  }

  def tweets = WebSocket.acceptWithActor[String, JsValue] {
    request => out => TwitterStreamer.props(out)
  }
}
